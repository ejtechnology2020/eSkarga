package pl.ejtechnology.tk.eSkarga.security;

import java.util.Optional;

/** Ochrona przed open redirect (T-055, ASVS V14.5): dozwolone tylko ścieżki względne w obrębie aplikacji. */
public final class SafeRedirect {

    private SafeRedirect() {
    }

    /** Zwraca ścieżkę, jeżeli jest bezpieczna (zaczyna się od pojedynczego „/”, bez schematu, hosta, „..”, znaków sterujących). */
    public static Optional<String> sanitize(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        String t = target.trim();
        if (!t.startsWith("/") || t.startsWith("//") || t.startsWith("/\\") || t.contains("..") || t.contains(":") || t.contains("\r") || t.contains("\n")
                || t.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f)) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    public static String orDefault(String target, String fallback) {
        return sanitize(target).orElse(fallback);
    }
}
