package pl.ejtechnology.tk.eSkarga.workers.transfer;

import java.util.Map;

import org.operaton.bpm.client.task.ExternalTask;

import pl.ejtechnology.tk.eSkarga.workers.AbstractIdempotentHandler;
import pl.ejtechnology.tk.eSkarga.workers.IdempotencyKey;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/**
 * Przekazanie sprawy do repertorium SK / K / U (UC-12, FR-19, BR-08): REST z kluczem idempotencji = sygnatura Ts/Tw.
 * [zaślepka] interfejs repertoriów merytorycznych – WireMock (T-028).
 */
public final class MeritTransferHandler extends AbstractIdempotentHandler {

    public MeritTransferHandler(WorkerConfig cfg) {
        super(cfg);
    }

    @Override
    protected String topic() {
        return Topics.PRZEKAZANIE_MERYTORYCZNE;
    }

    @Override
    protected Map<String, Object> handle(ExternalTask task, IdempotencyKey key) {
        throw new UnsupportedOperationException("Interfejs repertoriów SK/K/U nieznany (T-028). Klucz: " + key.value());
    }
}
