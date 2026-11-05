package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Wylogowanie (T-004). {@value #PATH} (GET, zwykły odnośnik z paska – bez postbacku JSF, który nie niesie nonce CSP PrimeFaces):
 * w trybie oidc ustawia ciasteczko {@link LoginFilter#LOGGED_OUT_COOKIE} i przekierowuje na {@value #ELYTRON_PATH} – {@code logout-path}
 * mechanizmu Elytron OIDC (zasób chroniony: mechanizm unieważnia sesję i odsyła do {@code end_session} ID-users z {@code id_token_hint},
 * ID-users wraca na {@code <baza>/}, a {@link LoginFilter} pokazuje stronę „sesja zakończona”). W trybie demo i gdy mechanizm nie przejął
 * {@value #ELYTRON_PATH} (brak sesji OIDC): koniec sesji lokalnej i strona „sesja zakończona”.
 */
@WebServlet(urlPatterns = {LogoutServlet.PATH, LogoutServlet.ELYTRON_PATH}, name = "wylogowanie")
public class LogoutServlet extends HttpServlet {

    public static final String PATH = "/wylogowanie";
    public static final String ELYTRON_PATH = "/wyloguj";
    static final String LOGGED_OUT_PAGE = "/blad/sesja.xhtml";

    @Inject
    OidcSettings settings;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (PATH.equals(req.getServletPath()) && !settings.isDemo() && LoginFilter.authenticated(req.getUserPrincipal())) {
            Cookie wylogowano = new Cookie(LoginFilter.LOGGED_OUT_COOKIE, "1");
            wylogowano.setPath(req.getContextPath() + "/");
            wylogowano.setMaxAge(120);
            wylogowano.setHttpOnly(true);
            wylogowano.setSecure(req.isSecure());
            resp.addCookie(wylogowano);
            resp.sendRedirect(req.getContextPath() + ELYTRON_PATH);
            return;
        }
        localLogout(req);
        resp.sendRedirect(req.getContextPath() + LOGGED_OUT_PAGE);
    }

    static void localLogout(HttpServletRequest req) {
        try {
            req.logout();
        } catch (ServletException ignored) {
            // bez mechanizmu uwierzytelniania (demo) logout kontenera nie jest dostępny
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
