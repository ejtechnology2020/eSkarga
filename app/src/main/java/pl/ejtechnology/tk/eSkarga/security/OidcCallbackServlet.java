package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Adres zwrotny OIDC ({@code redirectURI = ${baseURL}/callback}). Udane odpowiedzi (parametr {@code code}) przejmuje mechanizm
 * Jakarta Security przed serwletem; tu trafiają odpowiedzi z błędem dostawcy ({@code error}, {@code error_description}) – zamiast
 * 404 strona „Logowanie nie powiodło się” z oczyszczoną przyczyną – oraz wejścia bez parametrów (powrót na stronę główną).
 */
@WebServlet(urlPatterns = "/callback", name = "oidcCallback")
public class OidcCallbackServlet extends HttpServlet {

    static final String PAGE = "/blad/logowanie.xhtml";

    @Inject
    AuditService audit;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String error = req.getParameter("error");
        if (error == null || error.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }
        String kod = clean(error, 40);
        String opis = clean(req.getParameter("error_description"), 200);
        audit.recordDenial(LoginFilter.ANONYMOUS, null, "odmowa: ID-users odrzucił logowanie (" + kod + ")", opis, null, req.getRemoteAddr());
        req.setAttribute("oidcBlad", kod);
        req.setAttribute("oidcOpis", opis);
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        req.getRequestDispatcher(PAGE).forward(req, resp);
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
