package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingScan;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.Screen;

/**
 * Skany z rejestracji wpływu ({@code /wplyw/skan/<id>}, opcjonalnie {@code ?pobierz=1}) – podgląd w kolejce pism (S-06) i dzienniku (S-04).
 * Dostęp mają role ekranu kolejki (biuro podawcze, referent, kierownik; D4); odczyt jest audytowany, plik streamowany z S3 z obsługą Range
 * jak w {@link DocumentServlet}; przed pobraniem weryfikacja SHA-256 (T-030).
 */
@WebServlet(urlPatterns = "/wplyw/skan/*")
public class IncomingScanServlet extends HttpServlet {

    private static final Pattern PATH = Pattern.compile("^/(\\d+)$");

    @Inject
    private IncomingRegister incoming;
    @Inject
    private DocumentStore store;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;
    @Inject
    private OidcSettings settings;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Matcher m = PATH.matcher(req.getPathInfo() == null ? "" : req.getPathInfo());
        if (!m.matches()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!currentUser.isAuthenticated()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // role ekranów kolejki (referent) i dziennika wpływu (biuro podawcze, referent, kierownik) – D4
        Screen kolejka = Screen.byPath("/sprawy/kolejka.xhtml");
        Screen dziennik = Screen.byPath("/wplyw/dziennik.xhtml");
        boolean allowed = currentUser.getRoles().stream().anyMatch(r -> (kolejka != null && kolejka.visibleFor(r)) || (dziennik != null && dziennik.visibleFor(r)));
        if (!allowed) {
            audit.recordDenial(currentUser.getLogin(), currentUser.getRole().name(), "odmowa: skan wpływu poza rolą", m.group(1), null, req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (settings.isDemo()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Tryb demo: skany bez repozytorium");
            return;
        }
        Optional<IncomingScan> found = incoming.scan(Long.parseLong(m.group(1)));
        if (found.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        IncomingScan scan = found.get();
        boolean download = "1".equals(req.getParameter("pobierz"));
        Optional<DocumentStore.Range> range = download ? Optional.empty() : DocumentServlet.parseRange(req.getHeader("Range"));
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setContentType(scan.getContentType());
        resp.setHeader("X-Content-Type-Options", "nosniff");
        String nazwa = scan.getFileName().replaceAll("[^A-Za-z0-9._-]", "_");
        if (download) {
            if (!store.verifySha256(scan.getStorageKey(), scan.getSha256())) {
                audit.recordDenial(currentUser.getLogin(), currentUser.getRole().name(), "BŁĄD: niezgodny skrót SHA-256 skanu wpływu", scan.getLetter().getRegisterNo(), null, req.getRemoteAddr());
                resp.sendError(HttpServletResponse.SC_CONFLICT, "Skrót skanu niezgodny z zapisem");
                return;
            }
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + nazwa + "\"");
        } else {
            resp.setHeader("Content-Disposition", "inline; filename=\"" + nazwa + "\"");
            if (range.isPresent()) {
                resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            }
        }
        audit.record(currentUser.getLogin(), currentUser.getRole().name(), download ? "pobranie skanu wpływu" : "odczyt skanu wpływu", scan.getLetter().getRegisterNo(), null, req.getRemoteAddr());
        try (InputStream in = store.get(scan.getStorageKey(), range)) {
            in.transferTo(resp.getOutputStream());
        }
    }
}
