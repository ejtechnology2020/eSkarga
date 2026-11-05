package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.security.Principal;

import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
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
 * dopiero przy żądaniu zasobu chronionego albo jawnym {@link SecurityContext#authenticate} – bez tego filtra strony
 * renderowały się dla podmiotu anonimowego (Elytron zgłasza go jako principal „anonymous”) z domyślną rolą referenta.
 * W trybie demo filtr nic nie robi (ADR 0004). Strony błędów i zasoby JSF są dostępne bez logowania.
 */
public class LoginFilter implements Filter {

    public static final String ANONYMOUS = "anonymous";
    static final String FORBIDDEN_PAGE = "/blad/403.xhtml";

    @Inject
    OidcSettings settings;
    @Inject
    SecurityContext securityContext;
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
            AuthenticationStatus status = securityContext.authenticate(req, resp, AuthenticationParameters.withParams().newAuthentication(false));
            if (status == AuthenticationStatus.SUCCESS) {
                chain.doFilter(request, response);
            } else if (status != AuthenticationStatus.SEND_CONTINUE && !resp.isCommitted()) {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;   // SEND_CONTINUE: przekierowanie do Keycloaka ID-users już wysłane
        }
        if (currentUser.getRoles().isEmpty()) {
            audit.recordDenial(req.getUserPrincipal().getName(), null, "odmowa: brak ról eSkargi w tokenie ID-users", path, null, req.getRemoteAddr());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.getRequestDispatcher(FORBIDDEN_PAGE).forward(req, resp);
            return;
        }
        chain.doFilter(request, response);
    }
}
