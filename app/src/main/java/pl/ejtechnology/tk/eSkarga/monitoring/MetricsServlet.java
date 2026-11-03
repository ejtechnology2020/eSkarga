package pl.ejtechnology.tk.eSkarga.monitoring;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.operaton.bpm.engine.ProcessEngine;

import pl.ejtechnology.tk.eSkarga.common.security.Settings;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * {@code /metrics} w formacie Prometheus (T-054): JVM, zadania i incydenty Operaton, external tasks, kolejka OCR, terminy
 * bez czynności, wiek dziennika zdarzeń. Dostęp z sieci monitoringu (lista adresów {@code eskarga.metrics.allow}).
 */
@WebServlet(urlPatterns = "/metrics", name = "metrics")
public class MetricsServlet extends HttpServlet {

    @PersistenceContext(unitName = "repertorium")
    private EntityManager em;
    @Inject
    private ProcessEngine engine;
    @Inject
    private AppClock clock;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String allow = Settings.value("eskarga.metrics.allow", "127.0.0.1,::1,0:0:0:0:0:0:0:1");
        if (!allow.isBlank() && !allow.equals("*") && !java.util.Arrays.asList(allow.split(",")).contains(req.getRemoteAddr())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        StringBuilder sb = new StringBuilder();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        gauge(sb, "jvm_memory_heap_used_bytes", mem.getHeapMemoryUsage().getUsed());
        gauge(sb, "jvm_memory_heap_max_bytes", mem.getHeapMemoryUsage().getMax());
        gauge(sb, "jvm_threads", ManagementFactory.getThreadMXBean().getThreadCount());
        try {
            gauge(sb, "operaton_jobs_total", engine.getManagementService().createJobQuery().count());
            gauge(sb, "operaton_jobs_due", engine.getManagementService().createJobQuery().executable().count());
            gauge(sb, "operaton_jobs_failed", engine.getManagementService().createJobQuery().withException().count());
            gauge(sb, "operaton_incidents", engine.getRuntimeService().createIncidentQuery().count());
            gauge(sb, "operaton_external_tasks_open", engine.getExternalTaskService().createExternalTaskQuery().count());
            gauge(sb, "operaton_external_tasks_no_retries", engine.getExternalTaskService().createExternalTaskQuery().noRetriesLeft().count());
            gauge(sb, "operaton_process_instances_active", engine.getRuntimeService().createProcessInstanceQuery().active().count());
            gauge(sb, "operaton_user_tasks_open", engine.getTaskService().createTaskQuery().active().count());
        } catch (RuntimeException e) {
            gauge(sb, "operaton_up", 0);
        }
        try {
            LocalDate today = clock.today();
            gauge(sb, "eskarga_documents_pending_processing", count("select count(d) from CaseDocument d where d.storageKey is not null and d.signatureReport is null and d.pdfaCompliant = false and d.classified = false"));
            gauge(sb, "eskarga_deadlines_running", count("select count(x) from Deadline x where x.status = pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus.BIEGNIE"));
            gauge(sb, "eskarga_deadlines_overdue", em.createQuery("select count(x) from Deadline x where x.status = pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus.BIEGNIE and x.endsOn < :t", Long.class)
                    .setParameter("t", today).getSingleResult());
            gauge(sb, "eskarga_incoming_unqualified", count("select count(l) from IncomingLetter l where l.qualification is null"));
            gauge(sb, "eskarga_deliveries_unconfirmed", count("select count(d) from Delivery d where d.sentOn is not null and d.deliveredOn is null"));
            gauge(sb, "eskarga_audit_events_total", count("select count(e) from AuditEvent e"));
        } catch (RuntimeException e) {
            gauge(sb, "eskarga_db_up", 0);
        }
        resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(sb.toString());
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    static void gauge(StringBuilder sb, String name, long value) {
        sb.append("# TYPE ").append(name).append(" gauge\n").append(name).append(' ').append(value).append('\n');
    }
}
