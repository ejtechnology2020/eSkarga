package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.domain.model.GeneralLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/** Pisma niebędące skargami (T-051, UC-17, FR-30): rejestr, termin odpowiedzi (30 dni, konfigurowalny), odpowiedź z szablonu. */
@Stateless
public class GeneralLetterService {

    public static final int DEFAULT_REPLY_DAYS = 30;

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    IncomingRegister register;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public GeneralLetter register(long letterId, String subject, String userId) {
        IncomingLetter letter = em.find(IncomingLetter.class, letterId);
        register.qualify(letterId, LetterQualification.PISMO_OGOLNE, null, userId);
        LocalDate due = (letter.getFiledOn() == null ? clock.today() : letter.getReceivedAt().atZone(AppClock.WARSAW).toLocalDate()).plusDays(DEFAULT_REPLY_DAYS);
        GeneralLetter g = new GeneralLetter(letter, subject, due);
        em.persist(g);
        return g;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void replied(long id, String templateCode, String userId) {
        GeneralLetter g = em.find(GeneralLetter.class, id);
        g.replied(clock.today(), templateCode, null);
        audit.record(userId, "TK_REFERENT", "odpowiedź na pismo ogólne z szablonu " + templateCode, g.getLetter().getRegisterNo(), null, null);
    }

    public List<GeneralLetter> all() {
        return em.createQuery("select g from GeneralLetter g order by g.replyDueOn", GeneralLetter.class).getResultList();
    }

    public List<GeneralLetter> unanswered() {
        return em.createQuery("select g from GeneralLetter g where g.repliedOn is null order by g.replyDueOn", GeneralLetter.class).getResultList();
    }
}
