package pl.ejtechnology.tk.eSkarga.workers.transfer;

import java.util.Map;

import jakarta.json.JsonObject;

import org.operaton.bpm.client.task.ExternalTask;

import pl.ejtechnology.tk.eSkarga.workers.AbstractIdempotentHandler;
import pl.ejtechnology.tk.eSkarga.workers.IdempotencyKey;
import pl.ejtechnology.tk.eSkarga.workers.JsonHttp;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/**
 * Przekazanie sprawy do repertorium SK / K / U (UC-12, FR-19, T-028): dane sprawy z API aplikacji → POST do repertorium
 * merytorycznego z nagłówkiem {@code Idempotency-Key} (sygnatura Ts/Tw) → sygnatura powiązana jako zmienna procesu.
 * [zaślepka] API repertoriów SK/K/U – WireMock; ponowienie z tym samym kluczem zwraca tę samą sygnaturę.
 */
public final class MeritTransferHandler extends AbstractIdempotentHandler {

    public static final String VAR_RELATED_DOCKET = "sygnaturaPowiazana";

    private final JsonHttp http;

    public MeritTransferHandler(WorkerConfig cfg, JsonHttp http) {
        super(cfg);
        this.http = http;
    }

    @Override
    protected String topic() {
        return Topics.PRZEKAZANIE_MERYTORYCZNE;
    }

    @Override
    protected Map<String, Object> handle(ExternalTask task, IdempotencyKey key) {
        String sygnatura = task.getBusinessKey();
        JsonHttp.Reply data = http.get(cfg.appApiUrl() + "/sprawy/" + java.net.URLEncoder.encode(sygnatura, java.nio.charset.StandardCharsets.UTF_8) + "/przekazanie");
        if (!data.ok()) {
            throw new IllegalStateException("API aplikacji: HTTP " + data.status());
        }
        JsonObject caseData = data.json();
        String idem = caseData.getString("kluczIdempotencji", key.value());
        JsonHttp.Reply r = http.postJson(cfg.externalBaseUrl() + "/repertorium-sk/sprawy", caseData, Map.of("Idempotency-Key", idem));
        if (!r.ok() || r.json() == null || !r.json().containsKey("sygnatura")) {
            throw new IllegalStateException("Repertorium SK/K/U: HTTP " + r.status());
        }
        String related = r.json().getString("sygnatura");
        LOG.info("Sprawa {} przekazana jako {}", sygnatura, related);
        return Map.of(VAR_RELATED_DOCKET, related);
    }
}
