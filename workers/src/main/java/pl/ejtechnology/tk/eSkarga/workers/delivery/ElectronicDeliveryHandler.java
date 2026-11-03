package pl.ejtechnology.tk.eSkarga.workers.delivery;

import java.util.Map;

import org.operaton.bpm.client.task.ExternalTask;

import pl.ejtechnology.tk.eSkarga.workers.AbstractIdempotentHandler;
import pl.ejtechnology.tk.eSkarga.workers.IdempotencyKey;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/** e-Doręczenia / ePUAP: wysyłka z dowodem doręczenia (FR-25). [zaślepka] API – kontrakt przez WireMock (T-043). */
public final class ElectronicDeliveryHandler extends AbstractIdempotentHandler {

    public ElectronicDeliveryHandler(WorkerConfig cfg) {
        super(cfg);
    }

    @Override
    protected String topic() {
        return Topics.DORECZENIE_ELEKTRONICZNE;
    }

    @Override
    protected Map<String, Object> handle(ExternalTask task, IdempotencyKey key) {
        throw new UnsupportedOperationException("Adapter e-Doręczeń: kontrakt API nieznany (T-043). Klucz: " + key.value());
    }
}
