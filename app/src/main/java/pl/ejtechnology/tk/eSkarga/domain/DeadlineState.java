package pl.ejtechnology.tk.eSkarga.domain;

/** Stany wskaźnika terminu z arkusza komponentów (5 wariantów + brak terminu). */
public enum DeadlineState {
    NORMAL(""),
    /** ≤ 2 dni do końca. */
    WARNING("warn"),
    /** Po terminie. */
    OVERDUE("err"),
    /** Wstrzymany (art. 44, art. 78). */
    SUSPENDED("hold"),
    /** Wyliczenie pomocnicze – ocenia sędzia (art. 77). */
    AUXILIARY("aux"),
    /** Brak biegnącego terminu, tylko czas oczekiwania. */
    NONE("none");

    private final String css;

    DeadlineState(String css) {
        this.css = css;
    }

    public String css() {
        return css;
    }
}
