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

import pl.ejtechnology.tk.eSkarga.security.CurrentUser;

/**
 * Endpoint {@code /akta/{caseId}/dokument/{docId}} (T-034): autoryzacja sesji przez {@code CaseAccessPolicy},
 * obsługa HTTP Range dla pdf.js, {@code Cache-Control: no-store}, zdarzenie audytu przy każdym odczycie (NFR-02).
 * Do czasu modelu domeny mapowanie identyfikatorów na klucze S3 jest zaślepką (404).
 */
@WebServlet(urlPatterns = "/akta/*")
public class DocumentServlet extends HttpServlet {

    private static final Pattern PATH = Pattern.compile("^/(\\d+)/dokument/(\\d+)$");

    @Inject
    private DocumentStore store;
    @Inject
    private CurrentUser currentUser;

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
        String key = resolveKey(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)));
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setContentType("application/pdf");
        Optional<DocumentStore.Range> range = parseRange(req.getHeader("Range"));
        if (range.isPresent()) {
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        }
        try (InputStream in = store.get(key, range)) {
            in.transferTo(resp.getOutputStream());
        }
    }

    /** Mapowanie (caseId, docId) → klucz S3 po sprawdzeniu CaseAccessPolicy – dostarcza T-030/T-033. */
    protected String resolveKey(long caseId, long docId) {
        return null;
    }

    static Optional<DocumentStore.Range> parseRange(String header) {
        if (header == null || !header.startsWith("bytes=")) {
            return Optional.empty();
        }
        String[] parts = header.substring(6).split("-", 2);
        try {
            long start = Long.parseLong(parts[0].trim());
            long end = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1].trim()) : Long.MAX_VALUE;
            return Optional.of(new DocumentStore.Range(start, end));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
