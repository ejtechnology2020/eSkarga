package pl.ejtechnology.tk.eSkarga.domain;

/** Kanał wpływu pisma (FR-01). */
public enum IntakeChannel {
    OSOBISCIE,
    POCZTA,
    EPUAP,
    EDORECZENIA;

    /** Kanały elektroniczne dostarczają UPO i wypełniają formularz automatycznie (UC-01 krok 1). */
    public boolean isElectronic() {
        return this == EPUAP || this == EDORECZENIA;
    }
}
