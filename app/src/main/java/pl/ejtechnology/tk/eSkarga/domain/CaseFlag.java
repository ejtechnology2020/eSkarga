package pl.ejtechnology.tk.eSkarga.domain;

/** Flagi sprawy (brief §4.1). Pokazywane tylko, gdy aktywne; zawsze ikona i tekst. */
public enum CaseFlag {
    /** Wniosek o postanowienie tymczasowe – flaga pilności (art. 79, FR-21). */
    PILNA_ART_79("pil", "flag"),
    /** Zastrzeżenie nieujawniania danych – wymusza anonimizację (art. 80, FR-22). */
    ANONIMIZACJA_ART_80("an", "user-off"),
    /** Zawieszenie przy nadzwyczajnym środku zaskarżenia (art. 78, FR-20). */
    ZAWIESZONA("", "pause"),
    /** Wpis po przekazaniu do SK/K/U albo po archiwizacji (BR-08). */
    TYLKO_DO_ODCZYTU("", "lock");

    private final String css;
    private final String icon;

    CaseFlag(String css, String icon) {
        this.css = css;
        this.icon = icon;
    }

    public String css() {
        return css;
    }

    public String icon() {
        return icon;
    }
}
