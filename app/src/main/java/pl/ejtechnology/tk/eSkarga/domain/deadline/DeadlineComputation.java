package pl.ejtechnology.tk.eSkarga.domain.deadline;

import java.time.LocalDate;

/**
 * Wynik wyliczenia terminu.
 *
 * @param start        pierwszy dzień biegu terminu
 * @param nominalEnd   koniec według kalendarza, zanim uwzględniono dni wolne
 * @param end          koniec po przesunięciu na najbliższy dzień roboczy
 * @param suspendedDays liczba dni wstrzymania doliczonych do terminu
 * @param suspended    czy termin jest aktualnie wstrzymany (wstrzymanie otwarte)
 */
public record DeadlineComputation(LocalDate start, LocalDate nominalEnd, LocalDate end, long suspendedDays, boolean suspended) {

    public boolean movedToWorkingDay() {
        return !nominalEnd.equals(end);
    }
}
