package pl.ejtechnology.tk.eSkarga.domain.service;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.DeliveryChannel;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.domain.model.SignatureStatus;
import pl.ejtechnology.tk.eSkarga.documents.DocsGateway;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Podpis kwalifikowany dwuetapowy (D10, T-040, UC-07/UC-10): krok 1 – skrót do aplikacji lokalnej; krok 2 – złożenie podpisu,
 * nowa wersja dokumentu w repozytorium (Object Lock), status czynności „podpisana”, zmiana statusu sprawy, skierowanie do
 * wysyłki i ruch procesu – wszystko w jednej transakcji. Kolejne podpisy w składzie 3 sędziów: {@code finalSignature=false}
 * aż do ostatniego.
 */
@Stateless
public class SigningService {

    public static final int PROVISIONAL_WAIT_DAYS = 21;

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    DeliveryService deliveries;
    @Inject
    DocsGateway docs;
    @Inject
    DocumentStore store;
    @Inject
    TaskService taskService;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    /** Krok 1: dane do podpisu dla aplikacji lokalnej; nic nie zmienia w aktach. */
    public DocsGateway.DataToSign begin(long documentId, List<String> certificateChainBase64, String signatureAlgorithm, String userId) {
        CaseDocument doc = require(documentId);
        ProceduralAct act = actFor(doc).orElseThrow(() -> new IllegalStateException("Dokument nie jest czynnością do podpisu"));
        if (act.getStatus() != ActStatus.PROJEKT) {
            throw new IllegalStateException("Czynność jest już podpisana");
        }
        byte[] pdf;
        try (var in = store.get(doc.getStorageKey(), Optional.empty())) {
            pdf = in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Nie udało się odczytać dokumentu z repozytorium", e);
        }
        DocsGateway.DataToSign d = docs.dataToSign(pdf, certificateChainBase64, signatureAlgorithm, "Podpis: " + act.getKind() + " " + doc.getCaseFile().getDocket().format());
        audit.record(userId, "TK_SEDZIA", "rozpoczęcie podpisu (skrót przekazany do aplikacji lokalnej)", doc.getTitle(), doc.getCaseFile().getDocket().format(), null);
        return d;
    }

    /** Krok 2: złożenie podpisu i skutki w aktach, procesie i ekspedycji – jedna transakcja (D10). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ProceduralAct complete(long documentId, String sessionId, String signatureValueBase64, boolean finalSignature, LetterKind kind, String userId) {
        CaseDocument doc = require(documentId);
        CaseFile cf = doc.getCaseFile();
        ProceduralAct act = actFor(doc).orElseThrow(() -> new IllegalStateException("Dokument nie jest czynnością do podpisu"));
        byte[] signed = docs.signDocument(sessionId, signatureValueBase64);
        DocsGateway.Processed check = docs.process(signed, false);
        if (check.anySignature() && !check.allSignaturesValid()) {
            throw new IllegalStateException("Podpis nie przeszedł walidacji DSS – dokument odrzucony");
        }
        String key = doc.getStorageKey().replace(".pdf", "") + "-v" + (doc.getVersion() + 1) + ".pdf";
        DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(signed), signed.length, "application/pdf", finalSignature);
        doc.attachFile(stored.key(), stored.sha256());
        doc.setVersion(doc.getVersion() + 1);
        doc.setSignatureStatus(finalSignature ? SignatureStatus.KWALIFIKOWANY_WAZNY : SignatureStatus.CZESCIOWY);
        doc.setSignatureReport(truncate(check.signatureReportJson()));
        if (!finalSignature) {
            audit.record(userId, "TK_SEDZIA", "podpis członka składu złożony", doc.getTitle(), cf.getDocket().format(), null);
            return act;
        }
        act.setStatus(ActStatus.PODPISANA);
        if (kind.statusAfterSigning() != null) {
            cases.changeStatus(cf.getId(), kind.statusAfterSigning(), userId);
        }
        if (kind.deadlineVariable() != null) {
            // pismo czeka na doręczenie – wstępny termin (wysyłka + okres oczekiwania); właściwy ustawi rejestracja zwrotki (D6, T-041)
            deliveries.prepare(doc.getId(), channelFor(cf), userId);
        }
        if (kind.signTaskKey() != null) {
            Task t = taskService.createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).taskDefinitionKey(kind.signTaskKey()).singleResult();
            if (t != null) {
                if (kind.deadlineVariable() != null) {
                    Date provisional = Date.from(clock.today().plusDays(PROVISIONAL_WAIT_DAYS).plusDays(1).atStartOfDay(AppClock.WARSAW).toInstant());
                    taskService.complete(t.getId(), ProcessVariables.deadline(kind.deadlineVariable(), provisional));
                } else {
                    taskService.complete(t.getId());
                }
            }
        }
        audit.record(userId, "TK_SEDZIA", "podpis kwalifikowany złożony: " + kind.title(), doc.getTitle(), cf.getDocket().format(), null);
        return act;
    }

    private DeliveryChannel channelFor(CaseFile cf) {
        return deliveries.addresseeFor(cf).map(p -> p.getEDeliveryAddress() != null && !p.getEDeliveryAddress().isBlank() ? DeliveryChannel.EDORECZENIA : DeliveryChannel.POCZTA_POLECONY_ZPO)
                .orElse(DeliveryChannel.POCZTA_POLECONY_ZPO);
    }

    private CaseDocument require(long documentId) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        if (doc == null || doc.getStorageKey() == null) {
            throw new IllegalArgumentException("Brak dokumentu do podpisu: " + documentId);
        }
        return doc;
    }

    Optional<ProceduralAct> actFor(CaseDocument doc) {
        return em.createQuery("select a from ProceduralAct a where a.document = :d", ProceduralAct.class).setParameter("d", doc).getResultStream().findFirst();
    }

    static String truncate(String s) {
        return s == null ? null : s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
