package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Element listy kontrolnej wymogów formalnych (UC-06, FR-11). */
public record ChecklistItem(String wymog, String podstawa, State stan, String uwaga, boolean wstawDoPisma, String karta)
        implements Serializable {

    /** Stan wymogu: spełniony / brak / do sprawdzenia. */
    public enum State {
        SPELNIONY, BRAK, DO_SPRAWDZENIA
    }
}
