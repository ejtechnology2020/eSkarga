package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Wyzwalacz logowania ID-users. Ścieżka {@value #PATH} ({@code <baza>/callback} – adres zwrotny zarejestrowany w ID-users, T-037
 * projektu id) jest zasobem z ograniczeniem bezpieczeństwa w {@code web.xml} (dowolna rola {@code **}), więc żądanie do niej
 * obsługuje najpierw mechanizm Elytron OIDC: wysyła przekierowanie do Keycloaka ID-users (z {@code code_challenge} PKCE,
 * {@code redirect_uri} = ten sam adres), odbiera kod i dopiero potem przekazuje żądanie serwletowi. Programowe
 * {@code HttpServletRequest.authenticate()} z filtra nie dostarczało wyzwania (Undertow {@code UT010032}), dlatego
 * {@link LoginFilter} przekierowuje tu, zapamiętując w sesji adres, o który użytkownik prosił. Odpowiedź dostawcy z błędem
 * ({@code error}, {@code error_description}) przejmuje zwykle mechanizm; gdy dotrze tu – strona „Logowanie nie powiodło się”.
 */
@WebServlet(urlPatterns = {LoginTriggerServlet.PATH, "/logowanie"}, name = "logowanie")
public class LoginTriggerServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LoginTriggerServlet.class.getName());
    public static final String PATH = "/callback";
    /** Atrybut sesji z adresem (ścieżka w kontekście + zapytanie) do przywrócenia po zalogowaniu. */
    public static final String TARGET_ATTRIBUTE = "eskarga.login.cel";
    static final String DEFAULT_TARGET = "/pulpit.xhtml";
    static final String ERROR_PAGE = "/blad/logowanie.xhtml";

    @Inject
    AuditService audit;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String error = req.getParameter("error");
        if (error != null && !error.isBlank()) {
            String kod = clean(error, 40);
            String opis = clean(req.getParameter("error_description"), 200);
            audit.recordDenial(LoginFilter.ANONYMOUS, null, "odmowa: ID-users odrzucił logowanie (" + kod + ")", opis, null, req.getRemoteAddr());
            req.setAttribute("oidcBlad", kod);
            req.setAttribute("oidcOpis", opis);
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            req.getRequestDispatcher(ERROR_PAGE).forward(req, resp);
            return;
        }
        if (!LoginFilter.authenticated(req.getUserPrincipal())) {
            LOG.log(Level.WARNING, "Wejście na {0} bez uwierzytelnienia – ograniczenie bezpieczeństwa web.xml nie zadziałało (tryb demo?)", req.getServletPath());
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
        if (saved instanceof String s && s.startsWith("/") && !s.startsWith("//") && !s.startsWith(PATH) && !s.startsWith("/logowanie")
                && !s.startsWith("/wyloguj") && !s.contains("\\")) {
            return s;
        }
        return DEFAULT_TARGET;
    }

    /** Tylko litery, cyfry i podstawowa interpunkcja, bez znaczników – wartość pochodzi z adresu URL przekierowania. */
    static String clean(String value, int max) {
        if (value == null) {
            return "";
        }
        String s = value.replaceAll("[^\\p{L}\\p{N} _:+.,/()-]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
