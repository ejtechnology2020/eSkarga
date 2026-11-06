package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Element listy kontrolnej wymogów formalnych (UC-06, FR-11). */
public record ChecklistItem(String wymog, String podstawa, State stan, String uwaga, boolean wstawDoPisma, String karta, String kod)
        implements Serializable {

    /** Bez kodu wymogu (dane przykładowe) – kodem staje się opis wymogu. */
    public ChecklistItem(String wymog, String podstawa, State stan, String uwaga, boolean wstawDoPisma, String karta) {
        this(wymog, podstawa, stan, uwaga, wstawDoPisma, karta, wymog);
    }

    /** Stan wymogu: spełniony / brak / do sprawdzenia. */
    public enum State {
        SPELNIONY, BRAK, DO_SPRAWDZENIA
    }
}
