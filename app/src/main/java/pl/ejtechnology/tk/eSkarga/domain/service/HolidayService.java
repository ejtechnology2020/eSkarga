package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineCalculator;
import pl.ejtechnology.tk.eSkarga.domain.deadline.PolishHolidays;
import pl.ejtechnology.tk.eSkarga.domain.model.Holiday;

/** Kalendarz dni wolnych w Trybunale (T-025, S-24) jako źródło {@link DeadlineCalculator}. */
@Stateless
public class HolidayService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;

    public Set<LocalDate> extraDaysOff() {
        return em.createQuery("select h.day from Holiday h", LocalDate.class).getResultStream().collect(Collectors.toSet());
    }

    public List<Holiday> all() {
        return em.createQuery("select h from Holiday h order by h.day", Holiday.class).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Holiday add(LocalDate day, String description, String adminId) {
        Holiday h = new Holiday(day, description, adminId);
        em.persist(h);
        audit.record(adminId, "TK_ADMIN", "dodanie dnia wolnego", day.toString(), null, null);
        return h;
    }

    /** Kalkulator z aktualnym kalendarzem (święta ustawowe + dni wolne TK). */
    public DeadlineCalculator calculator() {
        return new DeadlineCalculator(new PolishHolidays(extraDaysOff()));
    }
}
