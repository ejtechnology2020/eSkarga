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
import pl.ejtechnology.tk.eSkarga.domain.model.Delegation;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.Screen;

/** Akt podstawowy upoważnienia (S-23) z repozytorium dokumentów: {@code GET /admin/upowaznienie/akt/{id}} – role ekranu upoważnień (D4), audyt wglądu. */
@WebServlet(urlPatterns = "/admin/upowaznienie/akt/*")
public class DelegationDocumentServlet extends HttpServlet {

    private static final Pattern PATH = Pattern.compile("^/(\\d+)$");

    @Inject
    private DelegationService delegations;
    @Inject
    private DocumentStore store;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Matcher m = PATH.matcher(req.getPathInfo() == null ? "" : req.getPathInfo());
        if (!m.matches()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Screen ekran = Screen.byPath("/admin/upowaznienia.xhtml");
        if (!currentUser.isAuthenticated() || ekran == null || currentUser.getRoles().stream().noneMatch(ekran::visibleFor)) {
            audit.recordDenial(currentUser.getLogin(), currentUser.getRole().name(), "odmowa: akt upoważnienia poza rolą", m.group(1), null, req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Optional<Delegation> found = delegations.byId(Long.parseLong(m.group(1)));
        if (found.isEmpty() || found.get().getBasisDocKey() == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Upoważnienie bez aktu w repozytorium");
            return;
        }
        Delegation d = found.get();
        if (d.getBasisDocSha256() != null && !store.verifySha256(d.getBasisDocKey(), d.getBasisDocSha256())) {
            audit.record(currentUser.getLogin(), currentUser.getRole().name(), "akt upoważnienia: niezgodny skrót SHA-256", d.getBasisDocKey(), null, req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_CONFLICT, "Skrót dokumentu niezgodny z zapisanym");
            return;
        }
        audit.record(currentUser.getLogin(), currentUser.getRole().name(), "wgląd w akt upoważnienia", String.valueOf(d.getId()), null, req.getRemoteAddr());
        String name = (d.getBasisDocRef() == null ? "akt" : d.getBasisDocRef()).replaceAll("[^A-Za-z0-9._-]", "_");
        resp.setContentType("application/pdf");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Content-Disposition", "inline; filename=\"" + (name.endsWith(".pdf") ? name : name + ".pdf") + "\"");
        try (InputStream in = store.get(d.getBasisDocKey(), Optional.empty())) {
            in.transferTo(resp.getOutputStream());
        }
    }
}
