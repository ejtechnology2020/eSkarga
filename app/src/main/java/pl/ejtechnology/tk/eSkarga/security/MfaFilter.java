package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.security.enterprise.identitystore.openid.OpenIdContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.common.security.JwtRs256Verifier;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Odmowa wejścia bez MFA (T-004): po zalogowaniu przez ID-users sprawdzamy claim ({@code acr}/{@code amr}) z tokenu ID
 * trzymanego w sesji serwera. Brak MFA → 403 i ekran S-26 „Wymagane uwierzytelnienie dwuskładnikowe” oraz zdarzenie
 * w dzienniku. W trybie demo filtr jest przezroczysty.
 */
public class MfaFilter implements Filter {

    public static final String MFA_PAGE = "/blad/mfa.xhtml";
    static final String SESSION_FLAG = "eskarga.mfa.ok";

    @Inject
    OidcSettings settings;
    @Inject
    Instance<OpenIdContext> openId;
    @Inject
    AuditService audit;
    @Inject
    SessionRegistry sessions;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getServletPath();
        if (settings.isDemo() || path.startsWith("/blad/") || path.startsWith("/jakarta.faces.resource/") || req.getUserPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }
        if (Boolean.TRUE.equals(req.getSession().getAttribute(SESSION_FLAG))) {
            chain.doFilter(request, response);
            return;
        }
        JsonObject claims = claims();
        if (MfaPolicy.satisfied(claims, settings.getMfaClaim(), settings.getMfaRequiredValue())) {
            req.getSession().setAttribute(SESSION_FLAG, Boolean.TRUE);
            if (claims != null) {
                sessions.register(req.getSession(), claims.getString("sub", null), claims.getString("sid", null));
            }
            chain.doFilter(request, response);
            return;
        }
        audit.recordDenial(req.getUserPrincipal().getName(), null, "odmowa: logowanie bez MFA (" + settings.getMfaClaim() + ")", path, null, req.getRemoteAddr());
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.getRequestDispatcher(MFA_PAGE).forward(req, resp);
    }

    /** Claimy tokenu ID (tam ID-users umieszcza {@code acr}/{@code amr} i {@code sid}); token zweryfikował już mechanizm Jakarta Security. */
    private JsonObject claims() {
        if (openId.isUnsatisfied()) {
            return null;
        }
        try {
            OpenIdContext ctx = openId.get();
            if (ctx.getIdentityToken() != null && ctx.getIdentityToken().getToken() != null) {
                String[] parts = ctx.getIdentityToken().getToken().split("\\.");
                if (parts.length == 3) {
                    return JwtRs256Verifier.json(parts[1]);
                }
            }
            return ctx.getClaimsJson();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
