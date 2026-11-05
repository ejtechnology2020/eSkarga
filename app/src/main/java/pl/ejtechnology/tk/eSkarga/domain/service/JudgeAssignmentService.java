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

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ProceduralAct assign(long caseId, String judgeUserId, String actingUserId, Set<AppRole> roles) {
        LocalDate today = clock.today();
        CaseFile cf = cases.require(caseId);
        delegations.requireActingAsPresident(actingUserId, roles, today, "wyznaczenie sędziego " + cf.getDocket().format());
        if (cf.current().getStatus() != CaseStatus.ZAREJESTROWANA && cf.current().getStatus() != CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " ma już wyznaczonego sędziego");
        }
        CaseDocument order = new CaseDocument(cf, "Zarządzenie Prezesa TK o wyznaczeniu sędziego", DocumentKind.CZYNNOSC, today, actingUserId);
        int last = cases.lastPage(cf);
        order.paginate(1, last + 1, last + 1);
        em.persist(order);
        ProceduralAct act = new ProceduralAct(cf, ProceduralActKind.ZARZADZENIE, "WYZNACZENIE_SEDZIEGO", clock.instant(), actingUserId, "art. 61 ust. 1", ActStatus.PODPISANA);
        act.setDocument(order);
        em.persist(act);
        em.persist(new CaseAssignment(cf, judgeUserId, AssignmentRole.JUDGE, today));
        // sędzia ustawiany przed zapisem wersji – wersje wpisu są tylko dopisywane (NFR-01)
        CaseFileVersion v = cases.changeStatus(caseId, CaseStatus.WE_WSTEPNEJ_KONTROLI, actingUserId, nv -> nv.setJudgeUserId(judgeUserId));
        // zadanie „Wyznacz sędziego” w procesie → sędzia staje się assignee zadania kontroli (${sedziaId})
        Task task = taskService.createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).taskDefinitionKey("T_Wyznacz").singleResult();
        if (task != null) {
            taskService.complete(task.getId(), ProcessVariables.judgeAssigned(judgeUserId));
        }
        audit.record(actingUserId, AppRole.TK_PREZES.name(), "wyznaczenie sędziego: " + judgeUserId, order.pagesLabel(), cf.getDocket().format(), null);
        return act;
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
