package pl.ejtechnology.tk.eSkarga.security;

import java.util.Optional;

import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.wildfly.security.http.oidc.OidcSecurityContext;

import pl.ejtechnology.tk.eSkarga.common.security.JwtRs256Verifier;

/**
 * Tokeny bieżącego logowania z mechanizmu Elytron OIDC: kontekst {@link OidcSecurityContext} leży w atrybucie żądania
 * (a przy {@code token-store=SESSION} także sesji) pod nazwą klasy. Zwraca pusty wynik, gdy użytkownik nie jest zalogowany
 * przez OIDC (tryb demo, zasoby publiczne) albo gdy moduł Elytrona nie jest dostępny (testy jednostkowe).
 */
public final class ElytronTokens {

    private ElytronTokens() {
    }

    public static Optional<OidcSecurityContext> context(HttpServletRequest req) {
        if (req == null) {
            return Optional.empty();
        }
        try {
            Object attr = req.getAttribute(OidcSecurityContext.class.getName());
            if (attr == null) {
                HttpSession session = req.getSession(false);
                attr = session == null ? null : session.getAttribute(OidcSecurityContext.class.getName());
            }
            return attr instanceof OidcSecurityContext ctx ? Optional.of(ctx) : Optional.empty();
        } catch (RuntimeException | NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    /** Treść (payload) tokenu dostępu jako JSON – źródło ról {@code resource_access.<client>.roles}. */
    public static Optional<JsonObject> accessTokenClaims(HttpServletRequest req) {
        return context(req).map(OidcSecurityContext::getTokenString).flatMap(ElytronTokens::payload);
    }

    /** Treść tokenu tożsamości (ID token) – {@code acr}, {@code sid}, {@code sub} dla polityki MFA i rejestru sesji. */
    public static Optional<JsonObject> idTokenClaims(HttpServletRequest req) {
        return context(req).map(OidcSecurityContext::getIDTokenString).flatMap(ElytronTokens::payload);
    }

    static Optional<JsonObject> payload(String jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(JwtRs256Verifier.json(parts[1]));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
