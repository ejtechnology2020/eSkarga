package pl.ejtechnology.tk.eSkarga.domain.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.SignatureStatus;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Dokumenty akt (T-030, T-031, FR-03): zapis skanu do repozytorium z SHA-256, kolejka przetwarzania (OCR, PDF/A, podpisy)
 * dla workera {@code dokument-przetworz}, zapis wyniku jako nowej wersji pliku. Dokumenty niejawne – tylko metadane (NFR-06).
 */
@Stateless
public class CaseDocumentService {

    /** Wynik przetworzenia z usługi dokumentów (przez workera). */
    public record ProcessingResult(byte[] pdf, String sha256, boolean pdfaCompliant, boolean ocrDone, String signatureReportJson, boolean anySignature,
                                   boolean allSignaturesValid) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    DocumentStore store;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    /** Skan pisma do akt: plik do S3 (SSE), SHA-256 w bazie, karty po ostatniej. Dokument niejawny: tylko metadane. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public CaseDocument upload(long caseId, String title, DocumentKind kind, byte[] content, String contentType, int pages, String userId) {
        CaseFile cf = cases.require(caseId);
        if (cf.current().getStatus().isReadOnly()) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " jest tylko do odczytu (BR-08)");
        }
        if (!"application/pdf".equals(contentType) || content == null || content.length < 5 || !new String(content, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw new IllegalArgumentException("Dozwolone są wyłącznie pliki PDF (sygnatura pliku), T-055");
        }
        CaseDocument doc = new CaseDocument(cf, title, kind, clock.today(), userId);
        int last = cases.lastPage(cf);
        doc.paginate(1, last + 1, last + Math.max(1, pages));
        if (kind != DocumentKind.NIEJAWNY) {
            String key = "akta/" + cf.getId() + "/" + UUID.randomUUID() + ".pdf";
            DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(content), content.length, contentType, false);
            doc.attachFile(stored.key(), stored.sha256());
        }
        em.persist(doc);
        audit.record(userId, "TK_BIURO_PODAWCZE", kind == DocumentKind.NIEJAWNY ? "dokument niejawny: tylko metadane" : "dołączenie skanu do akt", doc.pagesLabel(), cf.getDocket().format(), null);
        return doc;
    }

    /** Dokumenty z plikiem, które nie przeszły jeszcze przetworzenia (brak raportu podpisu i PDF/A). */
    public List<CaseDocument> pendingProcessing() {
        return em.createQuery("select d from CaseDocument d where d.storageKey is not null and d.signatureReport is null and d.pdfaCompliant = false "
                + "and d.classified = false order by d.id", CaseDocument.class).setMaxResults(50).getResultList();
    }

    public List<CaseDocument> searchByTitle(String query, int limit) {
        String like = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        return em.createQuery("select d from CaseDocument d where d.internalNote = false and lower(d.title) like :q order by d.id desc", CaseDocument.class)
                .setParameter("q", like).setMaxResults(limit).getResultList();
    }

    public CaseDocument require(long id) {
        CaseDocument d = em.find(CaseDocument.class, id);
        if (d == null) {
            throw new IllegalArgumentException("Brak dokumentu " + id);
        }
        return d;
    }

    public InputStream content(CaseDocument doc, Optional<DocumentStore.Range> range) {
        if (doc.getStorageKey() == null) {
            throw new IllegalStateException("Dokument bez pliku (niejawny albo metadane)");
        }
        return store.get(doc.getStorageKey(), range);
    }

    /** Wynik przetworzenia: nowa wersja pliku (gdy OCR/PDF/A zmieniły treść), flagi i raport podpisu. Powtórzony wynik jest neutralny. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public CaseDocument applyProcessing(long documentId, ProcessingResult result, String actor) {
        CaseDocument doc = require(documentId);
        if (doc.getSignatureReport() != null && doc.isPdfaCompliant() == result.pdfaCompliant()) {
            return doc; // idempotencja – worker ponowił zapis
        }
        if (result.pdf() != null && result.sha256() != null && !result.sha256().equalsIgnoreCase(doc.getSha256())) {
            String key = doc.getStorageKey().replace(".pdf", "") + "-v" + (doc.getVersion() + 1) + ".pdf";
            DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(result.pdf()), result.pdf().length, "application/pdf", false);
            doc.attachFile(stored.key(), stored.sha256());
            doc.setVersion(doc.getVersion() + 1);
        }
        doc.setPdfaCompliant(result.pdfaCompliant());
        doc.setOcrDone(result.ocrDone());
        doc.setSignatureReport(SigningService.truncate(result.signatureReportJson() == null ? "{}" : result.signatureReportJson()));
        doc.setSignatureStatus(!result.anySignature() ? SignatureStatus.BRAK : result.allSignaturesValid() ? SignatureStatus.KWALIFIKOWANY_WAZNY : SignatureStatus.NIEZWERYFIKOWANY);
        audit.record(actor, "WORKER", "przetworzenie dokumentu (PDF/A: " + result.pdfaCompliant() + ", OCR: " + result.ocrDone() + ", podpis: " + doc.getSignatureStatus() + ")",
                doc.pagesLabel(), doc.getCaseFile().getDocket().format(), null);
        return doc;
    }
}
