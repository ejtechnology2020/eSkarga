package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.web.Screen;

/**
 * Autoryzacja na poziomie ekranu: rola ogólna musi mieć prawo do ekranu z inwentarza (brief §3).
 * Uprawnienia do konkretnej sprawy sprawdza osobno {@link CaseAccessPolicy} przy każdym zapytaniu domenowym.
 */
public class ScreenAccessFilter implements Filter {

    @Inject
    private CurrentUser currentUser;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String viewId = http.getServletPath();
        Screen screen = Screen.byPath(viewId);
        if (screen != null && !currentUser.getRoles().stream().anyMatch(screen::visibleFor)) {
            ((HttpServletResponse) res).sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(req, res);
    }
}
