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
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.web.Fmt;

/**
 * Endpoint {@code /akta/dokument/{docId}} (T-030, T-034): autoryzacja sesji przez {@code CaseAccessPolicy}, HTTP Range dla pdf.js
 * (dokument 200 stron bez pobierania całości), {@code Cache-Control: no-store}, zdarzenie audytu przy każdym odczycie (NFR-02),
 * weryfikacja SHA-256 przy pełnym pobraniu, znak wodny „kto i kiedy” przy pobraniu ({@code ?pobierz=1}, brief §6).
 * Dokumenty niejawne nie mają pliku – 404 (NFR-06). W trybie demo bez bazy: 404.
 */
@WebServlet(urlPatterns = "/akta/*")
public class DocumentServlet extends HttpServlet {

    private static final Pattern PATH = Pattern.compile("^/(?:(\\d+)/)?dokument/(\\d+)$");

    @Inject
    private CaseDocumentService documents;
    @Inject
    private CaseAccessService access;
    @Inject
    private DocumentStore store;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;
    @Inject
    private OidcSettings settings;
    @Inject
    private DocsGateway docs;
    @Inject
    private AppClock clock;

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
        if (settings.isDemo()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Tryb demo: dokumenty bez repozytorium");
            return;
        }
        CaseDocument doc;
        try {
            doc = documents.require(Long.parseLong(m.group(2)));
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!access.canRead(doc.getCaseFile(), currentUser.getLogin(), currentUser.getRoles())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (doc.getStorageKey() == null || doc.isClassified()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Dokument bez pliku w systemie");
            return;
        }
        boolean download = "1".equals(req.getParameter("pobierz"));
        Optional<DocumentStore.Range> range = download ? Optional.empty() : parseRange(req.getHeader("Range"));
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setContentType("application/pdf");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        String caseRef = doc.getCaseFile().getDocket().format();
        if (download) {
            if (!store.verifySha256(doc.getStorageKey(), doc.getSha256())) {
                audit.recordDenial(currentUser.getLogin(), currentUser.getRole().name(), "BŁĄD: niezgodny skrót SHA-256 dokumentu", doc.pagesLabel(), caseRef, req.getRemoteAddr());
                resp.sendError(HttpServletResponse.SC_CONFLICT, "Skrót dokumentu niezgodny z zapisem w repertorium");
                return;
            }
            byte[] bytes;
            try (InputStream in = store.get(doc.getStorageKey(), Optional.empty())) {
                bytes = in.readAllBytes();
            }
            String stamp = "Pobrał: " + currentUser.getDisplayName() + ", " + Fmt.DATE_TIME.format(clock.now()) + ", " + caseRef + " " + doc.pagesLabel();
            byte[] marked = docs.watermark(bytes, stamp);
            audit.record(currentUser.getLogin(), currentUser.getRole().name(), "pobranie dokumentu ze znakiem wodnym", doc.pagesLabel(), caseRef, req.getRemoteAddr());
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + caseRef.replace(' ', '_').replace('/', '-') + "_" + doc.pagesLabel().replace(" ", "").replace(".", "") + ".pdf\"");
            resp.setContentLength(marked.length);
            resp.getOutputStream().write(marked);
            return;
        }
        if (range.isPresent()) {
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        }
        audit.record(currentUser.getLogin(), currentUser.getRole().name(), range.isPresent() ? "odczyt dokumentu (fragment)" : "odczyt dokumentu", doc.pagesLabel(), caseRef, req.getRemoteAddr());
        try (InputStream in = store.get(doc.getStorageKey(), range)) {
            in.transferTo(resp.getOutputStream());
        }
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
