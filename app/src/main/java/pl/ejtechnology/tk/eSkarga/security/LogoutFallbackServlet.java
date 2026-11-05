package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Ścieżka {@code /wyloguj} to {@code logout-path} mechanizmu Elytron OIDC ({@code oidc.json}): dla zalogowanego użytkownika
 * mechanizm sam unieważnia sesję i przekierowuje do {@code end_session} ID-users, zanim żądanie dotrze do serwletu.
 * Serwlet obsługuje pozostałe przypadki (brak sesji OIDC, tryb demo): kończy sesję lokalną i pokazuje stronę „sesja zakończona”.
 */
@WebServlet(urlPatterns = "/wyloguj", name = "wylogowanie")
public class LogoutFallbackServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.logout();
        } catch (ServletException ignored) {
            // bez mechanizmu uwierzytelniania (demo) logout kontenera nie jest dostępny
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        resp.sendRedirect(req.getContextPath() + "/blad/sesja.xhtml");
    }
}
