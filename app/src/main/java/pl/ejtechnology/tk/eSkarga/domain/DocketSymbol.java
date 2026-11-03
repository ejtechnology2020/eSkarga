package pl.ejtechnology.tk.eSkarga.domain;

/** Symbol repertorium. Ts i Tw są prowadzone w systemie; SK, K i U są celem przekazania (FR-19). */
public enum DocketSymbol {
    TS("Ts", true),
    TW("Tw", true),
    SK("SK", false),
    K("K", false),
    U("U", false);

    private final String code;
    private final boolean managedHere;

    DocketSymbol(String code, boolean managedHere) {
        this.code = code;
        this.managedHere = managedHere;
    }

    public String code() {
        return code;
    }

    /** Czy repertorium jest prowadzone w tym systemie (a nie tylko jako interfejs przekazania). */
    public boolean isManagedHere() {
        return managedHere;
    }

    public static DocketSymbol fromCode(String code) {
        for (DocketSymbol s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Nieznany symbol repertorium: " + code);
    }
}
