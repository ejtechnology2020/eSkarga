package pl.ejtechnology.tk.eSkarga.domain.deadline;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Wstrzymanie biegu terminu (BR-03): od złożenia wniosku o pełnomocnika z urzędu
 * do doręczenia rozstrzygnięcia sądu rejonowego (art. 44 ust. 3 ustawy o TK).
 *
 * @param from pierwszy dzień wstrzymania (włącznie)
 * @param to   ostatni dzień wstrzymania (włącznie); {@code null} = wstrzymanie trwa
 */
public record Suspension(LocalDate from, LocalDate to) {

    public Suspension {
        if (from == null) {
            throw new IllegalArgumentException("Wstrzymanie wymaga daty początkowej");
        }
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Koniec wstrzymania przed początkiem");
        }
    }

    public boolean isOpen() {
        return to == null;
    }

    /** Liczba dni wstrzymania (włącznie z obiema granicami). */
    public long days() {
        if (to == null) {
            throw new IllegalStateException("Wstrzymanie nie jest zakończone");
        }
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
