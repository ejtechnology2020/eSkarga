package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingCounter;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Dziennik wpływu (T-011, UC-01, FR-01 – FR-03). Numer dziennika {@code DW rrrr/nnnnn} jest nadawany z licznika pod blokadą wiersza,
 * w tej samej transakcji co wpis; po zarejestrowaniu pismo trafia do kolejki „Do kwalifikacji”. Dokument z klauzulą zapisuje tylko metadane.
 */
@Stateless
public class IncomingRegister {

    /** Dane z formularza S-03. */
    public record RegisterCommand(IntakeChannel channel, Instant receivedAt, LocalDate filedOn, boolean filedOnUncertain, String sender,
                                  int attachments, int copies, boolean classified, String userId, String remoteAddr) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public IncomingLetter register(RegisterCommand cmd) {
        if (cmd.channel() == null) {
            throw new IllegalArgumentException("Kanał wpływu jest wymagany");
        }
        if (cmd.sender() == null || cmd.sender().isBlank()) {
            throw new IllegalArgumentException("Nadawca jest wymagany");
        }
        if (cmd.channel().isElectronic() && cmd.filedOn() == null) {
            throw new IllegalArgumentException("Pismo elektroniczne musi mieć datę wniesienia z UPO (FR-02)");
        }
        Instant receivedAt = cmd.receivedAt() == null ? clock.instant() : cmd.receivedAt();
        int year = receivedAt.atZone(AppClock.WARSAW).getYear();
        String registerNo = nextRegisterNo(year);
        IncomingLetter letter = new IncomingLetter(registerNo, receivedAt, cmd.filedOn(), cmd.filedOnUncertain(), cmd.channel(), cmd.sender(),
                cmd.attachments(), cmd.copies(), cmd.classified(), cmd.userId());
        em.persist(letter);
        audit.record(cmd.userId(), "TK_BIURO_PODAWCZE", cmd.classified() ? "rejestracja wpływu (tylko metadane, klauzula)" : "rejestracja wpływu",
                registerNo, null, cmd.remoteAddr());
        return letter;
    }

    /** Kwalifikacja pisma (UC-02): skarga, wniosek, pismo do sprawy, pismo ogólne, do innej jednostki. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void qualify(long letterId, LetterQualification qualification, CaseFile caseFile, String userId) {
        IncomingLetter letter = em.find(IncomingLetter.class, letterId);
        if (letter == null) {
            throw new IllegalArgumentException("Brak pisma " + letterId);
        }
        if (letter.getQualification() != null) {
            throw new IllegalStateException("Pismo " + letter.getRegisterNo() + " jest już zakwalifikowane");
        }
        letter.qualify(qualification, caseFile);
        audit.record(userId, "TK_REFERENT", "kwalifikacja pisma: " + qualification, letter.getRegisterNo(),
                caseFile == null ? null : caseFile.getDocket().format(), null);
    }

    /** Pisma oczekujące na kwalifikację (kolejka S-06), najstarsze pierwsze. */
    public List<IncomingLetter> queue() {
        return em.createQuery("select l from IncomingLetter l where l.qualification is null order by l.receivedAt", IncomingLetter.class).getResultList();
    }

    public List<IncomingLetter> between(Instant from, Instant to) {
        return em.createQuery("select l from IncomingLetter l where l.receivedAt between :a and :b order by l.registerNo desc", IncomingLetter.class)
                .setParameter("a", from).setParameter("b", to).getResultList();
    }

    String nextRegisterNo(int year) {
        IncomingCounter counter = em.find(IncomingCounter.class, year, LockModeType.PESSIMISTIC_WRITE);
        if (counter == null) {
            counter = new IncomingCounter(year, 0);
            em.persist(counter);
            em.flush();
            counter = em.find(IncomingCounter.class, year, LockModeType.PESSIMISTIC_WRITE);
        }
        int next = counter.getLastValue() + 1;
        counter.setLastValue(next);
        return String.format("DW %d/%05d", year, next);
    }

    /** Pisma niezakwalifikowane dłużej niż {@code days} dni (monitor S-19, dziennik S-04). */
    public List<IncomingLetter> overdueQueue(int days) {
        Instant limit = clock.instant().minusSeconds(days * 86_400L);
        return em.createQuery("select l from IncomingLetter l where l.qualification is null and l.receivedAt < :limit order by l.receivedAt", IncomingLetter.class)
                .setParameter("limit", limit).getResultList();
    }
}
