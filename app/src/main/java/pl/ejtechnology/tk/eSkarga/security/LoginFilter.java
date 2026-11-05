package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Wymuszenie logowania przez ID-users (tryb {@code oidc}, D4): strony Facelets wymagają uwierzytelnionego użytkownika
 * z co najmniej jedną rolą eSkargi. Mechanizm {@code @OpenIdAuthenticationMechanismDefinition} (Jakarta Security) startuje
 * dopiero przy żądaniu zasobu chronionego albo jawnym {@link HttpServletRequest#authenticate} – bez tego filtra strony
 * renderowały się dla podmiotu anonimowego (Elytron zgłasza go jako principal „anonymous”) z domyślną rolą referenta.
 * W trybie demo filtr nic nie robi (ADR 0004). Strony błędów i zasoby JSF są dostępne bez logowania.
 */
public class LoginFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(LoginFilter.class.getName());
    public static final String ANONYMOUS = "anonymous";
    static final String FORBIDDEN_PAGE = "/blad/403.xhtml";

    @Inject
    OidcSettings settings;
    @Inject
    CurrentUser currentUser;
    @Inject
    AuditService audit;

    /** Czy podmiot jest naprawdę uwierzytelniony (nie null i nie anonimowy podmiot kontenera). */
    public static boolean authenticated(Principal p) {
        return p != null && !ANONYMOUS.equals(p.getName());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getServletPath();
        if (settings.isDemo() || path.startsWith("/blad/") || path.startsWith("/jakarta.faces.resource/")) {
            chain.doFilter(request, response);
            return;
        }
        if (!authenticated(req.getUserPrincipal())) {
            // HttpServletRequest.authenticate uruchamia mechanizm kontenera: na WildFly adnotację OIDC obsługuje podsystem
            // elytron-oidc-client (mechanizm Elytrona, nie bean Jakarta Security), więc SecurityContext.authenticate() nie działa.
            boolean ok;
            try {
                ok = req.authenticate(resp);
            } catch (ServletException | IllegalStateException e) {
                LOG.log(Level.WARNING, "Logowanie OIDC niedostępne dla " + path + " (providerUri " + settings.getProviderUri() + ")", e);
                if (!resp.isCommitted()) {
                    resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Logowanie przez ID-users niedostępne – szczegóły w logu serwera");
                }
                return;
            }
            if (!ok) {
                return;   // wyzwanie (przekierowanie do Keycloaka ID-users) już wysłane
            }
            if (!authenticated(req.getUserPrincipal())) {
                LOG.log(Level.WARNING, "authenticate() zakończone bez podmiotu dla {0}", path);
                if (!resp.isCommitted()) {
                    resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                }
                return;
            }
        }
        if (currentUser.getRoles().isEmpty()) {
            // diagnostyka (bez tokenów): czy kontekst Elytron OIDC jest widoczny i jakie role niesie token dostępu
            var ctx = ElytronTokens.context(req);
            var claims = ElytronTokens.accessTokenClaims(req);
            LOG.log(Level.WARNING, "Zalogowany {0} bez ról eSkargi: kontekst OIDC {1}, claims tokenu {2}, resource_access {3}, realm_access {4}, oczekiwana ścieżka ról {5}",
                    new Object[] {req.getUserPrincipal().getName(), ctx.isPresent() ? "obecny" : "BRAK (atrybut żądania/sesji " + ElytronTokens.ATTRIBUTE + ")",
                            claims.isPresent() ? "odczytane" : "brak", claims.map(c -> String.valueOf(c.get("resource_access"))).orElse("-"),
                            claims.map(c -> String.valueOf(c.get("realm_access"))).orElse("-"), settings.getRolesClaim()});
            audit.recordDenial(req.getUserPrincipal().getName(), null, "odmowa: brak ról eSkargi w tokenie ID-users", path, null, req.getRemoteAddr());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.getRequestDispatcher(FORBIDDEN_PAGE).forward(req, resp);
            return;
        }
        chain.doFilter(request, response);
    }
}
