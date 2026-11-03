package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tylko tryb demo: parametr {@code ?rola=ref|bp|asy|sed|pre|kie|arc|adm} przełącza wariant roli,
 * żeby pokazać ekrany tak jak na makietach (S-01 w trzech rolach itd.). W trybie OIDC filtr nic nie robi.
 */
public class DemoRoleFilter implements Filter {

    @Inject
    private OidcSettings settings;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (settings.isDemo() && req instanceof HttpServletRequest http) {
            String rola = http.getParameter("rola");
            if (rola != null && !rola.isBlank()) {
                try {
                    http.getSession(true).setAttribute(CurrentUser.DEMO_ROLE_ATTR, AppRole.fromShortCode(rola));
                } catch (IllegalArgumentException ignored) {
                    // nieznany kod roli – zostaje poprzednia
                }
            }
        }
        chain.doFilter(req, res);
    }
}
