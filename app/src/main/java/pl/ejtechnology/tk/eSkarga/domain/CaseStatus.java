package pl.ejtechnology.tk.eSkarga.domain;

/**
 * Statusy sprawy Ts/Tw z analizy wymagań (§4.2). Każdy wynika z art. 61 ustawy o TK.
 * Ton etykiety według briefu podglądu akt (§5.1).
 */
public enum CaseStatus {
    ZAREJESTROWANA(Tone.NEUTRAL, false, "minus"),
    OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO(Tone.NEUTRAL, false, "clock"),
    WE_WSTEPNEJ_KONTROLI(Tone.INFO, false, "search"),
    WEZWANIE_DO_USUNIECIA_BRAKOW(Tone.WARN, false, "alert-circle"),
    ODMOWA_TERMIN_NA_ZAZALENIE(Tone.WARN, false, "x"),
    ZAZALENIE_W_ROZPOZNANIU(Tone.INFO, false, "list"),
    ZAWIESZONA(Tone.OFF, false, "pause"),
    ZAKONCZONA_NADANO_BIEG(Tone.OK, true, "check"),
    ZAKONCZONA_ODMOWA_PRAWOMOCNA(Tone.OFF, true, "archive");

    private final Tone tone;
    private final boolean readOnly;
    private final String icon;

    CaseStatus(Tone tone, boolean readOnly, String icon) {
        this.tone = tone;
        this.readOnly = readOnly;
        this.icon = icon;
    }

    public Tone tone() {
        return tone;
    }

    /** Sprawa zakończona jest tylko do odczytu (BR-08). */
    public boolean isReadOnly() {
        return readOnly;
    }

    public String icon() {
        return icon;
    }

    public boolean isClosed() {
        return readOnly;
    }
}
