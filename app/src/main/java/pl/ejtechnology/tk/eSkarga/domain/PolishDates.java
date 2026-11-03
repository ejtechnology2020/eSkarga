package pl.ejtechnology.tk.eSkarga.domain;

import java.time.LocalDate;

/** Daty słownie do pism („3 listopada 2026”) – model pisma dostaje gotowy tekst (D13). */
public final class PolishDates {

    private static final String[] MONTHS_GENITIVE = {"stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września",
            "października", "listopada", "grudnia"};

    private PolishDates() {
    }

    /** {@code 2026-11-03 → "3 listopada 2026"} (bez „r.” – dodaje je szablon). */
    public static String words(LocalDate d) {
        if (d == null) {
            return "";
        }
        return d.getDayOfMonth() + " " + MONTHS_GENITIVE[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** {@code 03.11.2026} */
    public static String numeric(LocalDate d) {
        return d == null ? "" : String.format("%02d.%02d.%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
