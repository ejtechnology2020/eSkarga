package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.Screen;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;

/**
 * „Eksport roczny do PDF” z S-08: {@code GET /sprawy/repertorium/pdf?symbol=TS&rok=2026}. Role jak dla ekranu repertorium (D4),
 * wpisy po filtrze dostępu ({@link ViewData#casesIn}), zdarzenie audytu z liczbą wpisów (NFR-02). Treść bez danych spoza ekranu.
 */
@WebServlet(urlPatterns = "/sprawy/repertorium/pdf")
public class RepertoriumPdfServlet extends HttpServlet {

    @Inject
    private ViewData data;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AuditService audit;
    @Inject
    private AppClock clock;
    @Inject
    private Fmt fmt;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!currentUser.isAuthenticated()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Screen repertorium = Screen.byPath("/sprawy/repertorium.xhtml");
        if (repertorium == null || currentUser.getRoles().stream().noneMatch(repertorium::visibleFor)) {
            audit.recordDenial(currentUser.getLogin(), currentUser.getRole().name(), "odmowa: eksport repertorium poza rolą", null, null, req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        DocketSymbol symbol;
        int year;
        try {
            symbol = DocketSymbol.valueOf(req.getParameter("symbol") == null ? "TS" : req.getParameter("symbol").trim().toUpperCase());
            year = req.getParameter("rok") == null ? clock.today().getYear() : Integer.parseInt(req.getParameter("rok").trim());
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nieprawidłowy symbol repertorium albo rok");
            return;
        }
        if (year < 2000 || year > 2100) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nieprawidłowy rok");
            return;
        }
        List<CaseSummary> rows = data.casesIn(symbol, year).stream().sorted((a, b) -> Integer.compare(a.sygnatura().number(), b.sygnatura().number())).toList();
        LocalDate today = clock.today();
        byte[] pdf = new RepertoriumPdf(RepertoriumPdf.defaultFontDir(), s -> fmt.msg("status." + s)).render(symbol, year, rows, today, currentUser.getDisplayName());
        audit.record(currentUser.getLogin(), currentUser.getRole().name(), "eksport repertorium do PDF", symbol.code() + " " + year + " (" + rows.size() + " wpisów)", null, req.getRemoteAddr());
        resp.setContentType("application/pdf");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Content-Disposition", "attachment; filename=\"repertorium-" + symbol.code() + "-" + year + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }
}
