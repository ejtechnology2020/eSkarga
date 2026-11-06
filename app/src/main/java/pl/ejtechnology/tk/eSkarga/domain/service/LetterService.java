package pl.ejtechnology.tk.eSkarga.domain.service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.documents.DocsGateway;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Projekty pism z szablonów (T-026, UC-07, UC-10, FR-15, FR-16): aktywna wersja szablonu z bazy + model z danych sprawy →
 * PDF/A z usługi dokumentów → karta akt i czynność w stanie PROJEKT. Pismo pamięta wersję szablonu. Sporządzenie projektu
 * rozstrzyga zadanie „Przeprowadź wstępną kontrolę” (wynik kontroli), gdy proces na nie czeka.
 */
@Stateless
public class LetterService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    TemplateService templates;
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

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ProceduralAct draft(long caseId, LetterKind kind, Map<String, Object> extraModel, String judgeDisplayName, String userId) {
        CaseFile cf = cases.require(caseId);
        if (cf.current().getStatus().isReadOnly()) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " jest tylko do odczytu (BR-08)");
        }
        LetterTemplate template = templates.active(kind.name()).orElseThrow(() -> new IllegalStateException("Brak aktywnego szablonu " + kind.name()));
        Map<String, Object> model = LetterModelBuilder.build(cf, cases.parties(cf), clock.today(), judgeDisplayName, extraModel);
        byte[] pdf = docs.renderLetter(kind.name(), template.getSourceXhtml(), model);
        String key = "akta/" + cf.getId() + "/" + UUID.randomUUID() + ".pdf";
        DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(pdf), pdf.length, "application/pdf", false);

        CaseDocument doc = new CaseDocument(cf, kind.title(), DocumentKind.CZYNNOSC, clock.today(), userId);
        int last = cases.lastPage(cf);
        doc.paginate(1, last + 1, last + Math.max(1, pageCountEstimate(pdf)));
        doc.attachFile(stored.key(), stored.sha256());
        doc.setTemplateVersion(template);
        doc.setPdfaCompliant(true);
        em.persist(doc);
        String subtype = kind == LetterKind.POST_ODMOWA && extraModel != null && extraModel.get("podstawaOdmowy") != null
                ? "ODMOWA_PKT_" + extraModel.get("podstawaOdmowy") : kind.subtype();
        String basis = kind == LetterKind.POST_ODMOWA && extraModel != null && extraModel.get("podstawaOdmowy") != null
                ? kind.legalBasis() + " pkt " + extraModel.get("podstawaOdmowy") : kind.legalBasis();
        ProceduralAct act = new ProceduralAct(cf, kind.actKind(), subtype, clock.instant(), userId, basis, ActStatus.PROJEKT);
        act.setDocument(doc);
        em.persist(act);

        if (kind.decisionTaskKey() != null && kind.reviewResult() != null) {
            Task t = taskService.createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).taskDefinitionKey(kind.decisionTaskKey()).singleResult();
            if (t != null) {
                taskService.complete(t.getId(), ProcessVariables.reviewResult(kind.reviewResult()));
            }
        }
        audit.record(userId, "TK_SEDZIA", "projekt pisma: " + template.getCode() + " v" + template.getVersion(), doc.pagesLabel() + " " + kind.title(), cf.getDocket().format(), null);
        return act;
    }

    /** Odpowiedź na pismo ogólne (UC-17) – PDF bez karty akt sprawy; zwraca klucz w repozytorium. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public DocumentStore.Stored renderGeneralReply(Map<String, Object> model, String userId) {
        LetterTemplate template = templates.active(LetterKind_ODP).orElseThrow(() -> new IllegalStateException("Brak aktywnego szablonu odpowiedzi"));
        byte[] pdf = docs.renderLetter(LetterKind_ODP, template.getSourceXhtml(), model);
        String key = "pisma-ogolne/" + UUID.randomUUID() + ".pdf";
        DocumentStore.Stored stored = store.put(key, new ByteArrayInputStream(pdf), pdf.length, "application/pdf", false);
        audit.record(userId, "TK_REFERENT", "odpowiedź na pismo ogólne z szablonu " + template.getCode() + " v" + template.getVersion(), key, null, null);
        return stored;
    }

    static final String LetterKind_ODP = "ODP_PISMO_OGOLNE";

    public List<ProceduralAct> acts(CaseFile cf) {
        return em.createQuery("select a from ProceduralAct a where a.caseFile = :c order by a.actedOn", ProceduralAct.class).setParameter("c", cf).getResultList();
    }

    /** Szacunek liczby stron bez parsera PDF: liczba obiektów /Type /Page w pliku (dokładną paginację potwierdza referent). */
    public static int pageCountEstimate(byte[] pdf) {
        String s = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf("/Type /Page", idx)) >= 0) {
            int after = idx + "/Type /Page".length();
            if (after >= s.length() || s.charAt(after) != 's') {
                count++;
            }
            idx = after;
        }
        return Math.max(1, count);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
