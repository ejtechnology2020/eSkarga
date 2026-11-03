package pl.ejtechnology.tk.eSkarga.workers;

import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.client.backoff.ExponentialBackoffStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.ejtechnology.tk.eSkarga.workers.delivery.DeliveryHandler;
import pl.ejtechnology.tk.eSkarga.workers.documents.DocumentProcessingWorker;
import pl.ejtechnology.tk.eSkarga.workers.transfer.MeritTransferHandler;

/**
 * Workery integracji (D7, T-042, ADR 0010): jeden JAR z konfiguracją tematów. Pobierają zadania z silnika (fetch &amp; lock),
 * tylko one łączą się z systemami zewnętrznymi; ponawianie z rosnącym odstępem, incydent w Cockpicie po wyczerpaniu prób.
 * Token ID-users (client credentials) do REST silnika, API aplikacji i usługi dokumentów.
 */
public final class WorkersMain {

    private static final Logger LOG = LoggerFactory.getLogger(WorkersMain.class);

    private WorkersMain() {
    }

    public static void main(String[] args) {
        WorkerConfig cfg = WorkerConfig.fromEnvironment();
        OidcTokenInterceptor oidc = new OidcTokenInterceptor(cfg);
        JsonHttp http = new JsonHttp(oidc.tokens());
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl(cfg.engineRestUrl())
                .workerId(cfg.workerId())
                .maxTasks(cfg.maxTasks())
                .asyncResponseTimeout(20_000)
                .lockDuration(cfg.lockDurationMs())
                .backoffStrategy(new ExponentialBackoffStrategy(500L, 2, 60_000L))
                .addInterceptor(oidc)
                .build();

        if (cfg.topicEnabled(Topics.DORECZENIE_PISMA)) {
            client.subscribe(Topics.DORECZENIE_PISMA).handler(new DeliveryHandler(cfg, http)).open();
        }
        if (cfg.topicEnabled(Topics.PRZEKAZANIE_MERYTORYCZNE)) {
            client.subscribe(Topics.PRZEKAZANIE_MERYTORYCZNE).handler(new MeritTransferHandler(cfg, http)).open();
        }
        DocumentProcessingWorker documents = new DocumentProcessingWorker(cfg, http);
        if (cfg.topicEnabled(Topics.DOKUMENT_PRZETWORZ)) {
            documents.start();
        }
        WorkerMetrics.start(cfg);
        LOG.info("eSkarga workers uruchomione: {} → {}; tematy {}", cfg.workerId(), cfg.engineRestUrl(), cfg.topics());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.stop();
            documents.stop();
            WorkerMetrics.stop();
        }, "workers-shutdown"));
    }
}
