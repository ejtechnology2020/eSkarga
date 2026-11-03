package pl.ejtechnology.tk.eSkarga.security;

/**
 * Role ogólne aplikacji mapowane z claimów ID-users (D4, T-004). Nazwy claimów są konfigurowalne,
 * tutaj są tylko identyfikatory ról w aplikacji.
 */
public enum AppRole {
    TK_REFERENT("ref"),
    TK_ASYSTENT("asy"),
    TK_SEDZIA("sed"),
    TK_PREZES("pre"),
    TK_KIEROWNIK("kie"),
    TK_BIURO_PODAWCZE("bp"),
    TK_ARCHIWISTA("arc"),
    TK_ADMIN("adm");

    private final String shortCode;

    AppRole(String shortCode) {
        this.shortCode = shortCode;
    }

    /** Krótki kod używany w makietach i w podglądzie wariantów ról. */
    public String shortCode() {
        return shortCode;
    }

    public static AppRole fromShortCode(String code) {
        for (AppRole r : values()) {
            if (r.shortCode.equals(code) || r.name().equalsIgnoreCase(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Nieznana rola: " + code);
    }
}
