package pl.ejtechnology.tk.eSkarga.common.security;

/** Konfiguracja z właściwości systemowych WildFly albo zmiennych środowiskowych ({@code eskarga.oidc.issuer} ↔ {@code ESKARGA_OIDC_ISSUER}). */
public final class Settings {

    private Settings() {
    }

    public static String value(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return v == null || v.isBlank() ? def : v;
    }
}
