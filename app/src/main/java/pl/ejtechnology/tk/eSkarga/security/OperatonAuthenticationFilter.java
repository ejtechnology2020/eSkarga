package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.operaton.bpm.engine.IdentityService;

import pl.ejtechnology.tk.eSkarga.process.TaskBoardService;

/**
 * Operaton zna tożsamość z ID-users (T-004): na czas żądania {@code identityService.setAuthentication(userId, grupy)},
 * żeby zapytania o zadania i historia operacji silnika miały autora. Grupy = grupy kandydatów z ról aplikacji.
 */
public class OperatonAuthenticationFilter implements Filter {

    @Inject
    IdentityService identityService;
    @Inject
    CurrentUser currentUser;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean set = false;
        try {
            if (identityService != null && isAuthenticated((HttpServletRequest) request)) {
                List<String> groups = new ArrayList<>(TaskBoardService.groupsFor(currentUser.getRoles()));
                identityService.setAuthentication(currentUser.getLogin(), groups);
                set = true;
            }
            chain.doFilter(request, response);
        } finally {
            if (set) {
                identityService.clearAuthentication();
            }
        }
    }

    private boolean isAuthenticated(HttpServletRequest req) {
        try {
            return currentUser.isAuthenticated();
        } catch (RuntimeException e) {
            return req.getUserPrincipal() != null;
        }
    }
}
