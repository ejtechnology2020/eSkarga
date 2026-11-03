package pl.ejtechnology.tk.eSkarga.workers;

import java.util.Map;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wspólna baza handlerów: klucz idempotencji z identyfikatora zadania domenowego, ponawianie z rosnącym odstępem
 * (retries, retryTimeout) i incydent po wyczerpaniu prób. Zmienne procesu zawierają tylko identyfikatory (D8),
 * dane adresata handler pobiera z aplikacji po identyfikatorze.
 */
public abstract class AbstractIdempotentHandler implements ExternalTaskHandler {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractIdempotentHandler.class);
    public static final int MAX_RETRIES = 5;

    protected final WorkerConfig cfg;

    protected AbstractIdempotentHandler(WorkerConfig cfg) {
        this.cfg = cfg;
    }

    protected abstract String topic();

    /** Właściwa praca; zwraca zmienne do zapisania w procesie (tylko identyfikatory i daty). */
    protected abstract Map<String, Object> handle(ExternalTask task, IdempotencyKey key) throws Exception;

    @Override
    public void execute(ExternalTask task, ExternalTaskService service) {
        Object deliveryVar = task.getVariable("deliveryId");
        String deliveryId = deliveryVar != null ? String.valueOf(deliveryVar) : task.getBusinessKey();
        IdempotencyKey key = new IdempotencyKey(topic(), deliveryId);
        try {
            Map<String, Object> result = handle(task, key);
            service.complete(task, result);
        } catch (Exception e) {
            Integer retries = task.getRetries();
            int left = retries == null ? MAX_RETRIES : retries - 1;
            long backoff = backoffMs(MAX_RETRIES - left);
            LOG.warn("Zadanie {} ({}) nieudane, pozostało prób: {}, kolejna za {} ms", task.getId(), key.value(), left, backoff, e);
            service.handleFailure(task, e.getMessage(), stackTrace(e), left, backoff);
        }
    }

    /** Rosnący odstęp: 1 min, 2 min, 4 min, 8 min, 16 min (maks. 1 h). */
    public static long backoffMs(int attempt) {
        long base = 60_000L;
        long v = base << Math.max(0, Math.min(attempt, 6));
        return Math.min(v, 3_600_000L);
    }

    private static String stackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
