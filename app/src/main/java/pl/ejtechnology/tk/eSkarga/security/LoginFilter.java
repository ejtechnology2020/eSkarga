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
 * z co najmniej jedną rolą eSkargi. Mechanizm Elytron OIDC ({@code WEB-INF/oidc.json}) startuje dopiero przy żądaniu zasobu
 * chronionego – bez tego filtra strony renderowały się dla podmiotu anonimowego (Elytron zgłasza go jako principal „anonymous”)
 * z domyślną rolą referenta. Anonim jest przekierowywany na {@link LoginTriggerServlet} ({@code /logowanie}, jedyny zasób
 * z ograniczeniem bezpieczeństwa), a po zalogowaniu wraca na żądany adres. W trybie demo filtr nic nie robi (ADR 0004).
 * Strony błędów i zasoby JSF są dostępne bez logowania.
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
            // Mechanizm Elytron OIDC wysyła wyzwanie (302 do Keycloaka ID-users) tylko dla zasobu z ograniczeniem bezpieczeństwa –
            // programowe req.authenticate() kończyło się UT010032 bez przekierowania. Zapamiętujemy cel i idziemy na /logowanie.
            String target = req.getRequestURI().substring(req.getContextPath().length());
            if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                target = target + "?" + req.getQueryString();
            }
            req.getSession(true).setAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE, target);
            resp.sendRedirect(req.getContextPath() + LoginTriggerServlet.PATH);
            return;
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
