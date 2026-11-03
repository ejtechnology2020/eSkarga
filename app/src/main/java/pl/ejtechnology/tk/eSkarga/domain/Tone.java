package pl.ejtechnology.tk.eSkarga.domain;

/** Ton wizualny etykiety; w UI zawsze towarzyszy mu tekst (WCAG 2.1 AA). */
public enum Tone {
    NEUTRAL(""),
    INFO("info"),
    WARN("warn"),
    ERR("err"),
    OK("ok"),
    OFF("off"),
    NOTE("note");

    private final String css;

    Tone(String css) {
        this.css = css;
    }

    /** Sufiks klasy CSS z arkusza komponentów (np. „tag warn”). */
    public String css() {
        return css;
    }
}
