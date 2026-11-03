package pl.ejtechnology.tk.eSkarga.domain.deadline;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Kalkulator terminów (D6, BR-02 – BR-04). Termin wylicza domena, a timer BPMN dostaje gotową datę.
 * System sygnalizuje termin, ale go nie rozstrzyga – decyzja należy do sędziego (ryzyko §9.3 analizy).
 *
 * <p>Zasady (do potwierdzenia przez Biuro Służby Prawnej):
 * <ul>
 *   <li>termin w dniach biegnie od dnia następnego po doręczeniu, a nie od wysłania (BR-02, art. 61 ust. 3 i 5);</li>
 *   <li>koniec przypadający na sobotę, niedzielę albo dzień wolny przesuwa się na najbliższy dzień roboczy (BR-02);</li>
 *   <li>termin w miesiącach kończy się w dniu odpowiadającym dacie początkowej, a gdy go nie ma – w ostatnim dniu miesiąca;</li>
 *   <li>wniosek o pełnomocnika z urzędu wstrzymuje bieg terminu z art. 77 (BR-03, art. 44 ust. 3);</li>
 *   <li>data nadania u operatora wyznaczonego albo data UPO jest datą wniesienia (BR-04).</li>
 * </ul>
 */
public final class DeadlineCalculator {

    /** 7 dni na usunięcie braków i 7 dni na zażalenie (art. 61 ust. 3 i 5). */
    public static final int SEVEN_DAYS = 7;
    /** 3 miesiące od doręczenia ostatecznego orzeczenia (art. 77 ust. 1). */
    public static final int THREE_MONTHS = 3;
    /** Próg ostrzegawczy wskaźnika terminu (brief: ≤ 2 dni). */
    public static final int WARNING_THRESHOLD_DAYS = 2;

    private final HolidayCalendar calendar;

    public DeadlineCalculator(HolidayCalendar calendar) {
        this.calendar = Objects.requireNonNull(calendar, "kalendarz dni wolnych");
    }

    /** Termin 7-dniowy od doręczenia (braki, zażalenie). */
    public DeadlineComputation sevenDaysFromDelivery(LocalDate deliveryDate) {
        return daysFromEvent(deliveryDate, SEVEN_DAYS);
    }

    /**
     * Termin oznaczony w dniach. Dnia zdarzenia (doręczenia) nie wlicza się do terminu;
     * koniec przypadający na dzień wolny przesuwa się na najbliższy dzień roboczy.
     */
    public DeadlineComputation daysFromEvent(LocalDate eventDate, int days) {
        Objects.requireNonNull(eventDate, "data zdarzenia");
        if (days < 1) {
            throw new IllegalArgumentException("Liczba dni musi być dodatnia");
        }
        LocalDate start = eventDate.plusDays(1);
        LocalDate nominalEnd = eventDate.plusDays(days);
        LocalDate end = calendar.nextWorkingDayOnOrAfter(nominalEnd);
        return new DeadlineComputation(start, nominalEnd, end, 0, false);
    }

    /** Termin z art. 77: 3 miesiące od doręczenia ostatecznego orzeczenia, z wstrzymaniami (BR-03). */
    public DeadlineComputation threeMonthsFromRulingDelivery(LocalDate rulingDeliveryDate, List<Suspension> suspensions) {
        return monthsFromEvent(rulingDeliveryDate, THREE_MONTHS, suspensions);
    }

    /** Termin oznaczony w miesiącach z doliczeniem dni wstrzymania biegu. */
    public DeadlineComputation monthsFromEvent(LocalDate eventDate, int months, List<Suspension> suspensions) {
        Objects.requireNonNull(eventDate, "data zdarzenia");
        LocalDate start = eventDate.plusDays(1);
        LocalDate nominalEnd = eventDate.plusMonths(months);
        long suspendedDays = 0;
        boolean open = false;
        if (suspensions != null) {
            for (Suspension s : suspensions) {
                if (s.isOpen()) {
                    open = true;
                    continue;
                }
                // liczą się tylko dni wstrzymania, które przypadają w czasie biegu terminu
                LocalDate from = s.from().isBefore(start) ? start : s.from();
                LocalDate to = s.to();
                if (!to.isBefore(from)) {
                    suspendedDays += ChronoUnit.DAYS.between(from, to) + 1;
                }
            }
        }
        LocalDate extended = nominalEnd.plusDays(suspendedDays);
        LocalDate end = calendar.nextWorkingDayOnOrAfter(extended);
        return new DeadlineComputation(start, extended, end, suspendedDays, open);
    }

    /**
     * Czy pismo wniesiono w terminie (BR-04): liczy się data nadania u operatora wyznaczonego
     * albo data UPO, a nie data wpływu do Trybunału.
     */
    public boolean filedInTime(LocalDate filingDate, LocalDate deadlineEnd) {
        Objects.requireNonNull(filingDate, "data wniesienia");
        Objects.requireNonNull(deadlineEnd, "koniec terminu");
        return !filingDate.isAfter(deadlineEnd);
    }

    /** Liczba dni do końca terminu (ujemna po terminie). */
    public long daysLeft(LocalDate end, LocalDate today) {
        return ChronoUnit.DAYS.between(today, end);
    }

    /** Postęp biegu terminu w procentach (0–100) do paska wskaźnika. */
    public int progressPercent(LocalDate start, LocalDate end, LocalDate today) {
        long total = ChronoUnit.DAYS.between(start.minusDays(1), end);
        if (total <= 0) {
            return 100;
        }
        long elapsed = ChronoUnit.DAYS.between(start.minusDays(1), today);
        long pct = Math.round(elapsed * 100.0 / total);
        return (int) Math.max(0, Math.min(100, pct));
    }
}
