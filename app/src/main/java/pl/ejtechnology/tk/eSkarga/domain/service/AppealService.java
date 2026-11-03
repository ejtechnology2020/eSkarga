package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.model.Appeal;
import pl.ejtechnology.tk.eSkarga.domain.model.AppealOutcome;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Deadline;
import pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus;
import pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Zażalenie (T-027, UC-11, FR-18). Ocena terminowości jest pomocnicza (BR-04: data nadania/UPO, koniec terminu z {@link Deadline});
 * Prezes kieruje do składu 3 sędziów z wyłączeniem sędziego, który wydał odmowę (BR-07); od postanowienia o nieuwzględnieniu
 * nie można zarejestrować kolejnego zażalenia jako środka (tylko jako pismo ogólne).
 */
@Stateless
public class AppealService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    DelegationService delegations;
    @Inject
    PreliminaryReviewProcess process;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Appeal register(long caseId, LocalDate filedOn, String userId) {
        CaseFile cf = cases.require(caseId);
        if (cf.current().getStatus() != CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE) {
            throw new IllegalStateException("Zażalenie można zarejestrować tylko po doręczeniu postanowienia o odmowie");
        }
        boolean blocked = em.createQuery("select count(a) from Appeal a where a.caseFile = :c and a.outcome = :o", Long.class)
                .setParameter("c", cf).setParameter("o", AppealOutcome.NIEUWZGLEDNIONE).getSingleResult() > 0;
        if (blocked) {
            throw new IllegalStateException("Od postanowienia o nieuwzględnieniu zażalenia nie przysługuje kolejne zażalenie (BR-07) – zarejestruj jako pismo ogólne");
        }
        Boolean inTime = appealDeadline(cf).map(d -> !filedOn.isAfter(d.getEndsOn())).orElse(null);
        Appeal appeal = new Appeal(cf, filedOn, inTime);
        em.persist(appeal);
        cases.changeStatus(caseId, CaseStatus.ZAZALENIE_W_ROZPOZNANIU, userId);
        process.appealReceived(cf.getDocket(), inTime == null || inTime);
        audit.record(userId, AppRole.TK_REFERENT.name(), "rejestracja zażalenia (terminowość pomocniczo: " + inTime + ")", null, cf.getDocket().format(), null);
        return appeal;
    }

    /** Skierowanie do składu (Prezes TK albo osoba upoważniona, BR-10) z wykluczeniem sędziego odmowy (BR-07). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void referToPanel(long appealId, List<String> panelUserIds, LocalDate sessionOn, String userId, Set<AppRole> roles) {
        Appeal appeal = em.find(Appeal.class, appealId);
        CaseFile cf = appeal.getCaseFile();
        delegations.requireActingAsPresident(userId, roles, clock.today(), "skierowanie zażalenia " + cf.getDocket().format());
        if (panelUserIds.size() != 3) {
            throw new IllegalArgumentException("Skład rozpoznający zażalenie liczy 3 sędziów (art. 61 ust. 6)");
        }
        String refusingJudge = cf.current().getJudgeUserId();
        if (refusingJudge != null && panelUserIds.contains(refusingJudge)) {
            throw new IllegalArgumentException("Sędzia, który wydał odmowę, nie może orzekać o zażaleniu (BR-07)");
        }
        appeal.referToPanel(String.join(",", panelUserIds), sessionOn);
        audit.record(userId, AppRole.TK_PREZES.name(), "skierowanie zażalenia do składu", String.join(",", panelUserIds) + " · " + sessionOn, cf.getDocket().format(), null);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void decide(long appealId, AppealOutcome outcome, String userId) {
        Appeal appeal = em.find(Appeal.class, appealId);
        appeal.decide(outcome);
        CaseFile cf = appeal.getCaseFile();
        CaseStatus next = switch (outcome) {
            case UWZGLEDNIONE -> CaseStatus.ZAKONCZONA_NADANO_BIEG;
            case NIEUWZGLEDNIONE, BEZ_ROZPOZNANIA -> CaseStatus.ZAKONCZONA_ODMOWA_PRAWOMOCNA;
        };
        cases.changeStatus(cf.getId(), next, userId);
        audit.record(userId, AppRole.TK_SEDZIA.name(), "postanowienie składu: " + outcome, null, cf.getDocket().format(), null);
    }

    public Optional<Deadline> appealDeadline(CaseFile cf) {
        return em.createQuery("select d from Deadline d where d.caseFile = :c and d.kind = :k and d.status <> :rev order by d.createdAt desc", Deadline.class)
                .setParameter("c", cf).setParameter("k", DeadlineType.ZAZALENIE).setParameter("rev", DeadlineStatus.UCHYLONY)
                .getResultStream().findFirst();
    }

    public List<Appeal> all() {
        return em.createQuery("select a from Appeal a order by a.filedOn desc", Appeal.class).getResultList();
    }
}
