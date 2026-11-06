package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;

/**
 * Podgląd wersji szablonu pisma (S-24): {@code GET /admin/szablony/podglad?id=…} → PDF z usługi dokumentów na danych fikcyjnych
 * (Ts 312/26, D13). Tylko administrator (ekran S-24); nic nie zapisuje; błąd renderowania wraca jako 409 z komunikatem.
 */
@WebServlet(urlPatterns = "/admin/szablony/podglad")
public class TemplatePreviewServlet extends HttpServlet {

    @Inject
    private TemplateService templates;
    @Inject
    private DocsGateway docs;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!currentUser.isAuthenticated() || !currentUser.getRoles().contains(AppRole.TK_ADMIN)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        long id;
        try {
            id = Long.parseLong(req.getParameter("id"));
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Brak identyfikatora szablonu");
            return;
        }
        LetterTemplate t = templates.find(id).orElse(null);
        if (t == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Brak szablonu");
            return;
        }
        byte[] pdf;
        try {
            pdf = docs.previewTemplate(t.getCode(), t.getSourceXhtml());
        } catch (IllegalStateException e) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
        }
        audit.record(currentUser.getLogin(), "TK_ADMIN", "podgląd szablonu", t.getCode() + " v" + t.getVersion(), null, null);
        resp.setContentType("application/pdf");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Content-Disposition", "inline; filename=\"szablon-" + t.getCode().toLowerCase() + "-v" + t.getVersion() + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }
}
