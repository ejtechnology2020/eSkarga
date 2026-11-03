package pl.ejtechnology.tk.eSkarga.workers.delivery;

import java.util.Map;

import org.operaton.bpm.client.task.ExternalTask;

import pl.ejtechnology.tk.eSkarga.workers.AbstractIdempotentHandler;
import pl.ejtechnology.tk.eSkarga.workers.IdempotencyKey;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/** Operator pocztowy (Elektroniczny Nadawca): książka nadawcza, numery nadania, e-zwrotki. [zaślepka] T-043. */
public final class PostalDeliveryHandler extends AbstractIdempotentHandler {

    public PostalDeliveryHandler(WorkerConfig cfg) {
        super(cfg);
    }

    @Override
    protected String topic() {
        return Topics.DORECZENIE_POCZTOWE;
    }

    @Override
    protected Map<String, Object> handle(ExternalTask task, IdempotencyKey key) {
        throw new UnsupportedOperationException("Adapter operatora pocztowego: kontrakt API nieznany (T-043). Klucz: " + key.value());
    }
}
