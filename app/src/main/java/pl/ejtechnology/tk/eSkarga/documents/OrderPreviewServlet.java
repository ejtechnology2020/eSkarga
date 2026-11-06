package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.JudgeAssignmentService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import pl.ejtechnology.tk.eSkarga.web.model.JudgeLoad;

/**
 * Projekt zarządzenia o wyznaczeniu sędziego (S-17) renderowany z aktywnego szablonu rejestru szablonów pism:
 * {@code GET /czynnosci/wyznaczenie/podglad?sygnatura=Ts 1/26&sedzia=login}. Tylko podgląd (nic nie zapisuje), dla osób
 * z dostępem do akt sprawy (D4). Zapis zarządzenia do akt następuje przy „Wyznacz i podpisz zarządzenie”.
 */
@WebServlet(urlPatterns = "/czynnosci/wyznaczenie/podglad")
public class OrderPreviewServlet extends HttpServlet {

    @Inject
    private CaseFileService cases;
    @Inject
    private CaseAccessService access;
    @Inject
    private JudgeAssignmentService judges;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private ViewData data;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!currentUser.isAuthenticated()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String sygnatura = req.getParameter("sygnatura");
        String sedzia = req.getParameter("sedzia");
        CaseFile cf;
        try {
            cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElse(null);
        } catch (IllegalArgumentException e) {
            cf = null;
        }
        if (cf == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Brak sprawy");
            return;
        }
        if (!access.canRead(cf, currentUser.getLogin(), currentUser.getRoles())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String nazwa = sedzia == null ? "…" : data.judgeLoads().stream().filter(j -> sedzia.equals(j.sedzia())).map(JudgeLoad::nazwisko).findFirst().orElse(sedzia);
        byte[] pdf;
        try {
            pdf = judges.renderOrder(cf, nazwa);
        } catch (IllegalStateException e) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
        }
        resp.setContentType("application/pdf");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Content-Disposition", "inline; filename=\"projekt-zarzadzenia.pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }
}
