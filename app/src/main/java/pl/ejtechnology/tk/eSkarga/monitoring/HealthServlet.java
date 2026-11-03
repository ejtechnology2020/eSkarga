package pl.ejtechnology.tk.eSkarga.monitoring;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.operaton.bpm.engine.ProcessEngine;

import pl.ejtechnology.tk.eSkarga.search.SearchIndex;

/** {@code /health} (T-054, NFR-07): baza, silnik procesów, indeks wyszukiwania; 503 gdy składnik krytyczny nie odpowiada. Bez danych osobowych. */
@WebServlet(urlPatterns = "/health", name = "health")
public class HealthServlet extends HttpServlet {

    @PersistenceContext(unitName = "repertorium")
    private EntityManager em;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SearchIndex search;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> checks = new LinkedHashMap<>();
        boolean up = true;
        try {
            em.createNativeQuery("select 1").getSingleResult();
            checks.put("baza", "UP");
        } catch (RuntimeException e) {
            checks.put("baza", "DOWN");
            up = false;
        }
        try {
            engine.getManagementService().getProperties();
            checks.put("operaton", "UP");
        } catch (RuntimeException e) {
            checks.put("operaton", "DOWN");
            up = false;
        }
        checks.put("wyszukiwanie", search != null && search.available() ? "UP" : "DEGRADED");
        resp.setStatus(up ? 200 : 503);
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        StringBuilder sb = new StringBuilder("{\"status\":\"").append(up ? "UP" : "DOWN").append("\",\"skladniki\":{");
        boolean first = true;
        for (var e : checks.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        resp.getWriter().write(sb.append("}}").toString());
    }
}
