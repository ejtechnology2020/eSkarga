package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;

/**
 * Kod błędu dla administratora na stronie 500 (S-26): pozwala odnaleźć wpis w logach bez ujawniania szczegółów technicznych.
 * Ten sam kod trafia do logu serwera (logger {@code pl.ejtechnology.tk.eSkarga.blad}, poziom SEVERE) razem z adresem żądania
 * i wyjątkiem, który wywołał błąd, oraz do dziennika audytu (zdarzenie „błąd aplikacji”). Analiza: {@code logi.cmd app | Select-String RTS-500-…}
 * (Windows) albo {@code docker compose logs app | grep RTS-500-…} – wpis z kodem poprzedza ślad stosu Undertow (UT005023) tego samego żądania.
 */
@Named("errorCode")
@RequestScoped
public class ErrorCodeBean implements Serializable {

    private static final Logger LOG = Logger.getLogger("pl.ejtechnology.tk.eSkarga.blad");

    @Inject
    private AppClock clock;
    @Inject
    private HttpServletRequest request;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;

    private final String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    private String code;

    public String getCode() {
        if (code == null) {
            code = "RTS-500-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").format(clock.now()) + "-" + suffix;
            register();
        }
        return code;
    }

    /** Zapis kodu do logu i audytu – raz na żądanie, odporny na brak atrybutów błędu (strona otwarta wprost). */
    private void register() {
        Throwable cause = (Throwable) attribute(RequestDispatcher.ERROR_EXCEPTION);
        Object uri = attribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object status = attribute(RequestDispatcher.ERROR_STATUS_CODE);
        String where = uri == null ? (request == null ? "?" : request.getRequestURI()) : uri.toString();
        String summary = cause == null ? "bez wyjątku (status " + status + ")" : rootCause(cause).toString();
        LOG.log(Level.SEVERE, () -> "Błąd " + code + ": " + where + " – " + summary);
        if (cause != null) {
            LOG.log(Level.SEVERE, "Ślad stosu dla " + code, cause);
        }
        try {
            String login = currentUser == null ? null : currentUser.getLogin();
            audit.record(login == null ? "anonim" : login, null, "błąd aplikacji " + code, where, null, request == null ? null : request.getRemoteAddr());
        } catch (RuntimeException e) {
            // audyt nie może przesłonić pierwotnego błędu (np. gdy padła baza) – kod jest już w logu
            LOG.log(Level.WARNING, "Nie zapisano zdarzenia audytu dla " + code + ": " + e);
        }
    }

    private Object attribute(String name) {
        return request == null ? null : request.getAttribute(name);
    }

    static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
