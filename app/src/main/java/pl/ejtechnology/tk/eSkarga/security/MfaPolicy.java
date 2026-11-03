package pl.ejtechnology.tk.eSkarga.security;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Wymóg MFA (T-004, NFR-04): claim i wymagana wartość są konfigurowalne, bo kontrakt ID-users nie jest znany.
 * Obsługiwane kształty: {@code acr} jako tekst (równość) i {@code amr} jako tablica metod (zawiera wartość).
 */
public final class MfaPolicy {

    private MfaPolicy() {
    }

    public static boolean satisfied(JsonObject claims, String claimName, String requiredValue) {
        if (claims == null || claimName == null || claimName.isBlank()) {
            return false;
        }
        if (requiredValue == null || requiredValue.isBlank()) {
            return true; // MFA wymusza sam dostawca – aplikacja nic nie sprawdza (ADR 0002)
        }
        JsonValue v = claims.get(claimName);
        if (v == null) {
            return false;
        }
        return switch (v.getValueType()) {
            case STRING -> matches(((JsonString) v).getString(), requiredValue);
            case ARRAY -> ((JsonArray) v).getValuesAs(JsonString.class).stream().anyMatch(s -> matches(s.getString(), requiredValue));
            default -> false;
        };
    }

    /** Lista dopuszczalnych wartości rozdzielona przecinkami, np. {@code urn:tk:acr:mfa,mfa,2}. */
    private static boolean matches(String actual, String required) {
        for (String r : required.split(",")) {
            if (r.trim().equals(actual)) {
                return true;
            }
        }
        return false;
    }
}
