package pl.ejtechnology.tk.eSkarga.domain;

/** Rodzaje terminów liczonych przez domenę (D6). Podstawa prawna w etykiecie UI. */
public enum DeadlineType {
    /** 7 dni od doręczenia zarządzenia o usunięciu braków (art. 61 ust. 3). */
    BRAKI("art. 61 ust. 3"),
    /** 7 dni od doręczenia postanowienia o odmowie (art. 61 ust. 5). */
    ZAZALENIE("art. 61 ust. 5"),
    /** 3 miesiące od doręczenia ostatecznego orzeczenia – wyliczenie pomocnicze (art. 77). */
    ART_77("art. 77"),
    /** Oczekiwanie na potwierdzenie odbioru (okres techniczny, konfigurowalny). */
    ZWROTKA(""),
    /** Termin posiedzenia niejawnego składu. */
    POSIEDZENIE(""),
    /** Termin odpowiedzi na pismo ogólne (FR-30). */
    ODPOWIEDZ(""),
    /** Oczekiwanie bez biegnącego terminu procesowego. */
    OCZEKIWANIE("");

    private final String legalBasis;

    DeadlineType(String legalBasis) {
        this.legalBasis = legalBasis;
    }

    public String legalBasis() {
        return legalBasis;
    }
}
