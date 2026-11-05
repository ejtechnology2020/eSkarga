package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Wyzwalacz logowania ID-users. Ścieżka {@value #PATH} jest jedynym zasobem z ograniczeniem bezpieczeństwa w {@code web.xml}
 * (dowolna rola {@code **}), więc żądanie do niej obsługuje najpierw mechanizm Elytron OIDC: wysyła przekierowanie do Keycloaka
 * ID-users (z {@code code_challenge} PKCE), odbiera kod na tym samym adresie i dopiero potem przekazuje żądanie serwletowi.
 * Programowe {@code HttpServletRequest.authenticate()} z filtra nie dostarczało wyzwania (Undertow {@code UT010032}), dlatego
 * {@link LoginFilter} przekierowuje tu, zapamiętując w sesji adres, o który użytkownik prosił.
 */
@WebServlet(urlPatterns = LoginTriggerServlet.PATH, name = "logowanie")
public class LoginTriggerServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LoginTriggerServlet.class.getName());
    public static final String PATH = "/logowanie";
    /** Atrybut sesji z adresem (ścieżka w kontekście + zapytanie) do przywrócenia po zalogowaniu. */
    public static final String TARGET_ATTRIBUTE = "eskarga.login.cel";
    static final String DEFAULT_TARGET = "/pulpit.xhtml";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!LoginFilter.authenticated(req.getUserPrincipal())) {
            LOG.log(Level.WARNING, "Wejście na {0} bez uwierzytelnienia – ograniczenie bezpieczeństwa web.xml nie zadziałało (tryb demo?)", PATH);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Logowanie przez ID-users niedostępne – szczegóły w logu serwera");
            return;
        }
        resp.sendRedirect(req.getContextPath() + target(req.getSession(false)));
    }

    /** Adres docelowy z sesji; akceptowana tylko ścieżka względna wobec kontekstu (bez hosta, bez {@code //}). */
    static String target(HttpSession session) {
        if (session == null) {
            return DEFAULT_TARGET;
        }
        Object saved = session.getAttribute(TARGET_ATTRIBUTE);
        session.removeAttribute(TARGET_ATTRIBUTE);
        if (saved instanceof String s && s.startsWith("/") && !s.startsWith("//") && !s.startsWith(PATH) && !s.contains("\\")) {
            return s;
        }
        return DEFAULT_TARGET;
    }
}
