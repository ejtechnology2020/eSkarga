package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralActKind;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Wyznaczenie sędziego do wstępnej kontroli (T-023, UC-05, FR-14, art. 61 ust. 1). Czynność Prezesa TK albo osoby upoważnionej
 * (BR-10): zarządzenie w aktach, przydział do sprawy, sędzia jako assignee zadania kontroli, zdarzenie audytu – jedna transakcja.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class JudgeAssignmentService {

    /** Obciążenie sędziego do podglądu w S-17 (FR-14). */
    public record JudgeLoad(String judgeUserId, long casesInReview) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    DelegationService delegations;
    @Inject
    TaskService taskService;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @Inject
    TemplateService templates;
    @Inject
    pl.ejtechnology.tk.eSkarga.documents.DocsGateway docs;
    @Inject
    pl.ejtechnology.tk.eSkarga.documents.DocumentStore store;
    @Inject
    JudgeDictionaryService judgeDictionary;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_PREZES, AppRole.TK_SEDZIA})
    public ProceduralAct assign(long caseId, String judgeUserId, String actingUserId, Set<AppRole> roles) {
        return assign(caseId, judgeUserId, judgeUserId, actingUserId, roles);
    }

    /**
     * Wyznaczenie jednoetapowe (API, testy, migracje danych): projekt zarządzenia i od razu skutki – bez podpisu elektronicznego.
     * Ekran S-17 używa {@link #draftOrder} + podpisu na S-16 ({@link SigningService#complete} → {@link #finalizeAssignment}).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_PREZES, AppRole.TK_SEDZIA})
    public ProceduralAct assign(long caseId, String judgeUserId, String judgeDisplayName, String actingUserId, Set<AppRole> roles) {
        ProceduralAct act = draftOrder(caseId, judgeUserId, judgeDisplayName, actingUserId, roles);
        act.setStatus(ActStatus.PODPISANA);
        finalizeAssignment(act, actingUserId);
        return act;
    }

    /**
     * UC-05 krok 1: projekt zarządzenia Prezesa TK o wyznaczeniu sędziego – PDF/A z aktywnego szablonu {@code ZARZ_WYZNACZENIE_SEDZIEGO}
     * (rejestr szablonów pism) w repozytorium i w spisie akt, czynność w stanie PROJEKT z loginem sędziego ({@code panelUserIds}).
     * Wymaga ważnego upoważnienia (BR-10). Istniejący projekt dla tej sprawy i tego sędziego jest zwracany ponownie (bez duplikatu).
     * Skutki wyznaczenia następują po podpisie ({@link #finalizeAssignment}).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_PREZES, AppRole.TK_SEDZIA})
    public ProceduralAct draftOrder(long caseId, String judgeUserId, String judgeDisplayName, String actingUserId, Set<AppRole> roles) {
        LocalDate today = clock.today();
        CaseFile cf = cases.require(caseId);
        delegations.requireActingAsPresident(actingUserId, roles, today, "wyznaczenie sędziego " + cf.getDocket().format());
        requireAwaiting(cf);
        if (judgeUserId == null || judgeUserId.isBlank()) {
            throw new IllegalArgumentException("Wybierz sędziego");
        }
        // ADR 0027: tylko sędzia ze słownika, aktywny i w kadencji w dniu zarządzenia; nazwa do pisma ze słownika, gdy nie podano
        pl.ejtechnology.tk.eSkarga.domain.model.Judge judge = judgeDictionary.requireEligible(judgeUserId, today);
        if (judgeDisplayName == null || judgeDisplayName.isBlank()) {
            judgeDisplayName = judge.titledName();
        }
        for (ProceduralAct existing : pendingOrders(cf)) {
            if (judgeUserId.equals(existing.getPanelUserIds())) {
                return existing;
            }
        }
        LetterKind kind = LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO;
        pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate template = orderTemplate();
        byte[] pdf = renderOrder(cf, judgeDisplayName == null || judgeDisplayName.isBlank() ? judgeUserId : judgeDisplayName);
        String key = "akta/" + cf.getId() + "/" + java.util.UUID.randomUUID() + ".pdf";
        pl.ejtechnology.tk.eSkarga.documents.DocumentStore.Stored stored = store.put(key, new java.io.ByteArrayInputStream(pdf), pdf.length, "application/pdf", false);
        CaseDocument order = new CaseDocument(cf, kind.title(), DocumentKind.CZYNNOSC, today, actingUserId);
        int last = cases.lastPage(cf);
        order.paginate(1, last + 1, last + Math.max(1, LetterService.pageCountEstimate(pdf)));
        order.attachFile(stored.key(), stored.sha256());
        order.setTemplateVersion(template);
        order.setPdfaCompliant(true);
        em.persist(order);
        ProceduralAct act = new ProceduralAct(cf, ProceduralActKind.ZARZADZENIE, kind.subtype(), clock.instant(), actingUserId, kind.legalBasis(), ActStatus.PROJEKT);
        act.setDocument(order);
        act.setPanelUserIds(judgeUserId);
        em.persist(act);
        audit.record(actingUserId, AppRole.TK_PREZES.name(), "projekt zarządzenia o wyznaczeniu sędziego: " + judgeUserId, order.pagesLabel(), cf.getDocket().format(), null);
        return act;
    }

    /**
     * UC-05 krok 2 (po podpisie zarządzenia): przydział sędziego, wersja wpisu „we wstępnej kontroli” (NFR-01), zamknięcie zadania
     * „Wyznacz sędziego” w procesie (sędzia staje się assignee kontroli), zdarzenie audytu – jedna transakcja z podpisem.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_SEDZIA, AppRole.TK_PREZES})
    public void finalizeAssignment(ProceduralAct act, String actingUserId) {
        CaseFile cf = act.getCaseFile();
        String judgeUserId = act.getPanelUserIds();
        if (judgeUserId == null || judgeUserId.isBlank()) {
            throw new IllegalStateException("Zarządzenie bez wskazanego sędziego");
        }
        requireAwaiting(cf);
        LocalDate today = clock.today();
        em.persist(new CaseAssignment(cf, judgeUserId, AssignmentRole.JUDGE, today));
        // sędzia ustawiany przed zapisem wersji – wersje wpisu są tylko dopisywane (NFR-01)
        cases.changeStatus(cf.getId(), CaseStatus.WE_WSTEPNEJ_KONTROLI, actingUserId, nv -> nv.setJudgeUserId(judgeUserId));
        // zadanie „Wyznacz sędziego” w procesie → sędzia staje się assignee zadania kontroli (${sedziaId})
        Task task = taskService.createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).taskDefinitionKey("T_Wyznacz").singleResult();
        if (task != null) {
            taskService.complete(task.getId(), ProcessVariables.judgeAssigned(judgeUserId));
        }
        audit.record(actingUserId, AppRole.TK_PREZES.name(), "wyznaczenie sędziego: " + judgeUserId, act.getDocument() == null ? null : act.getDocument().pagesLabel(), cf.getDocket().format(), null);
    }

    /** Projekty zarządzeń o wyznaczeniu sędziego czekające na podpis (S-17 ponowne wejście, S-16). */
    public List<ProceduralAct> pendingOrders(CaseFile cf) {
        return em.createQuery("select a from ProceduralAct a where a.caseFile = :c and a.subtype = :s and a.status = :st order by a.id desc", ProceduralAct.class)
                .setParameter("c", cf).setParameter("s", LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO.subtype()).setParameter("st", ActStatus.PROJEKT).getResultList();
    }

    private static void requireAwaiting(CaseFile cf) {
        if (cf.current().getStatus() != CaseStatus.ZAREJESTROWANA && cf.current().getStatus() != CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " ma już wyznaczonego sędziego");
        }
    }

    /** Aktywny szablon zarządzenia o wyznaczeniu sędziego z rejestru szablonów pism (S-24). */
    public pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate orderTemplate() {
        return templates.active(LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO.name())
                .orElseThrow(() -> new IllegalStateException("Brak aktywnego szablonu " + LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO.name()
                        + " – Administracja → Słowniki i szablony pism → „Załaduj szablony domyślne”"));
    }

    /** Projekt zarządzenia (PDF) z aktywnego szablonu dla sprawy i sędziego – do podglądu na S-17 i do zapisu przy wyznaczeniu; nic nie zapisuje. */
    @RequiresRole
    public byte[] renderOrder(CaseFile cf, String judgeDisplayName) {
        pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate template = orderTemplate();
        java.util.Map<String, Object> model = LetterModelBuilder.build(cf, cases.parties(cf), clock.today(), judgeDisplayName, null);
        return docs.renderLetter(LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO.name(), template.getSourceXhtml(), model);
    }

    /** Sprawy oczekujące na wyznaczenie sędziego (S-17). */
    public List<CaseFile> awaiting() {
        return em.createQuery("select v.caseFile from CaseFileVersion v where v.status in (:s) and v.version = "
                        + "(select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile and (x.correction = false or exists (select d from CorrectionDecision d where d.version = x and d.decision = 'ZATWIERDZONA')))"
                        + " order by v.caseFile.enteredOn", CaseFile.class)
                .setParameter("s", List.of(CaseStatus.ZAREJESTROWANA, CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO)).getResultList();
    }

    /** Liczba spraw we wstępnej kontroli na sędziego (obciążenie, FR-14, FR-32). */
    public List<JudgeLoad> loads() {
        return em.createQuery("select v.judgeUserId, count(v) from CaseFileVersion v where v.judgeUserId is not null and v.status in (:s) and v.version = "
                        + "(select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile and (x.correction = false or exists (select d from CorrectionDecision d where d.version = x and d.decision = 'ZATWIERDZONA')))"
                        + " group by v.judgeUserId order by v.judgeUserId", Object[].class)
                .setParameter("s", List.of(CaseStatus.WE_WSTEPNEJ_KONTROLI, CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW, CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE))
                .getResultStream().map(r -> new JudgeLoad((String) r[0], (Long) r[1])).toList();
    }
}
