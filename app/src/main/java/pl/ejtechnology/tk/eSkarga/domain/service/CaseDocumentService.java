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
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
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
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class CaseDocumentService {

    /** Wynik przetworzenia z usługi dokumentów (przez workera). */
    public record ProcessingResult(byte[] pdf, String sha256, boolean pdfaCompliant, boolean ocrDone, String signatureReportJson, boolean anySignature,
                                   boolean allSignaturesValid, String text, String convertedFrom) {
        public ProcessingResult(byte[] pdf, String sha256, boolean pdfaCompliant, boolean ocrDone, String signatureReportJson, boolean anySignature, boolean allSignaturesValid) {
            this(pdf, sha256, pdfaCompliant, ocrDone, signatureReportJson, anySignature, allSignaturesValid, null, null);
        }

        public ProcessingResult(byte[] pdf, String sha256, boolean pdfaCompliant, boolean ocrDone, String signatureReportJson, boolean anySignature,
                                boolean allSignaturesValid, String text) {
            this(pdf, sha256, pdfaCompliant, ocrDone, signatureReportJson, anySignature, allSignaturesValid, text, null);
        }
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
    @Inject
    pl.ejtechnology.tk.eSkarga.search.SearchService search;

    /** Skan pisma do akt: plik do S3 (SSE), SHA-256 w bazie, karty po ostatniej. Dokument niejawny: tylko metadane. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole
    public CaseDocument upload(long caseId, String title, DocumentKind kind, byte[] content, String contentType, int pages, String userId) {
        CaseFile cf = cases.require(caseId);
        if (cf.current().getStatus().isReadOnly()) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " jest tylko do odczytu (BR-08)");
        }
        String detected = pl.ejtechnology.tk.eSkarga.documents.FileTypes.detect(content);
        if (kind != DocumentKind.NIEJAWNY && (detected == null || !pl.ejtechnology.tk.eSkarga.documents.FileTypes.isCaseDocument(detected) || (contentType != null && !contentType.equals(detected)))) {
            throw new IllegalArgumentException("Dozwolone są pliki PDF, DOCX i DOC (sygnatura pliku), T-055 / ADR 0026");
        }
        CaseDocument doc = new CaseDocument(cf, title, kind, clock.today(), userId);
        int last = cases.lastPage(cf);
        doc.paginate(1, last + 1, last + Math.max(1, pages));
        if (kind != DocumentKind.NIEJAWNY) {
            String key = "akta/" + cf.getId() + "/" + UUID.randomUUID() + "." + pl.ejtechnology.tk.eSkarga.documents.FileTypes.extension(detected);
            DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(content), content.length, detected, false);
            doc.attachFile(stored.key(), stored.sha256());
            if (pl.ejtechnology.tk.eSkarga.documents.FileTypes.isOffice(detected)) {
                doc.markSource(detected, title);
            }
        }
        em.persist(doc);
        if (search != null) {
            search.indexDocument(doc, null);
        }
        audit.record(userId, "TK_BIURO_PODAWCZE", kind == DocumentKind.NIEJAWNY ? "dokument niejawny: tylko metadane" : "dołączenie skanu do akt", doc.pagesLabel(), cf.getDocket().format(), null);
        return doc;
    }

    /** Dokumenty z plikiem, które nie przeszły jeszcze przetworzenia (brak raportu podpisu i PDF/A). */
    @RequiresRole(workers = true)
    public List<CaseDocument> pendingProcessing() {
        return em.createQuery("select d from CaseDocument d where d.storageKey is not null and d.signatureReport is null and d.pdfaCompliant = false "
                + "and d.classified = false order by d.id", CaseDocument.class).setMaxResults(50).getResultList();
    }

    public List<CaseDocument> searchByTitle(String query, int limit) {
        String like = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        return em.createQuery("select d from CaseDocument d where d.internalNote = false and lower(d.title) like :q order by d.id desc", CaseDocument.class)
                .setParameter("q", like).setMaxResults(limit).getResultList();
    }

    /** Dokument razem ze sprawą i jej bieżącą wersją (fetch join) – serwlet dokumentów czyta {@code getCaseFile()} poza transakcją EJB. */
    @RequiresRole(workers = true)
    public CaseDocument require(long id) {
        CaseDocument d = em.createQuery("select d from CaseDocument d join fetch d.caseFile where d.id = :id", CaseDocument.class).setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
        if (d == null) {
            throw new IllegalArgumentException("Brak dokumentu " + id);
        }
        d.getCaseFile().getDocket();
        if (d.getCaseFile().current() != null) {
            d.getCaseFile().current().getStatus();   // inicjalizacja wersji przed opuszczeniem transakcji (canRead, isReadOnly)
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
    @RequiresRole(workers = true)
    public CaseDocument applyProcessing(long documentId, ProcessingResult result, String actor) {
        CaseDocument doc = require(documentId);
        if (doc.getSignatureReport() != null && doc.isPdfaCompliant() == result.pdfaCompliant()) {
            return doc; // idempotencja – worker ponowił zapis
        }
        if (result.pdf() != null && result.sha256() != null && !result.sha256().equalsIgnoreCase(doc.getSha256())) {
            String original = doc.getStorageKey();
            String key = original.replaceFirst("\\.[A-Za-z0-9]+$", "") + "-v" + (doc.getVersion() + 1) + ".pdf";
            DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(result.pdf()), result.pdf().length, "application/pdf", false);
            doc.attachFile(stored.key(), stored.sha256());
            doc.setVersion(doc.getVersion() + 1);
            if (result.convertedFrom() != null && !result.convertedFrom().isBlank()) {
                doc.markConverted(original);   // ADR 0026: oryginał DOC/DOCX zostaje w repozytorium, kartą akt jest PDF/A
            }
        }
        doc.setPdfaCompliant(result.pdfaCompliant());
        doc.setOcrDone(result.ocrDone());
        doc.setSignatureReport(SigningService.truncate(result.signatureReportJson() == null ? "{}" : result.signatureReportJson()));
        doc.setSignatureStatus(!result.anySignature() ? SignatureStatus.BRAK : result.allSignaturesValid() ? SignatureStatus.KWALIFIKOWANY_WAZNY : SignatureStatus.NIEZWERYFIKOWANY);
        if (search != null) {
            search.indexDocument(doc, result.text());
        }
        audit.record(actor, "WORKER", "przetworzenie dokumentu (PDF/A: " + result.pdfaCompliant() + ", OCR: " + result.ocrDone() + ", podpis: " + doc.getSignatureStatus()
                + (result.convertedFrom() == null || result.convertedFrom().isBlank() ? "" : ", z " + result.convertedFrom().toUpperCase()) + ")",
                doc.pagesLabel(), doc.getCaseFile().getDocket().format(), null);
        return doc;
    }
}
