package pl.ejtechnology.tk.eSkarga.workers;

import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.client.backoff.ExponentialBackoffStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.ejtechnology.tk.eSkarga.workers.delivery.ElectronicDeliveryHandler;
import pl.ejtechnology.tk.eSkarga.workers.delivery.PostalDeliveryHandler;
import pl.ejtechnology.tk.eSkarga.workers.transfer.MeritTransferHandler;

/**
 * Workery integracji (D7, T-042): jeden JAR z konfiguracją tematów. Pobierają zadania z silnika (fetch &amp; lock),
 * tylko one łączą się z systemami zewnętrznymi; ponawianie z rosnącym odstępem, incydent w Cockpicie po wyczerpaniu prób.
 * Uwierzytelnienie do REST silnika tokenem ID-users (client credentials) dostarcza {@link OidcTokenInterceptor}.
 */
public final class WorkersMain {

    private static final Logger LOG = LoggerFactory.getLogger(WorkersMain.class);

    private WorkersMain() {
    }

    public static void main(String[] args) {
        WorkerConfig cfg = WorkerConfig.fromEnvironment();
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl(cfg.engineRestUrl())
                .workerId(cfg.workerId())
                .maxTasks(cfg.maxTasks())
                .asyncResponseTimeout(20_000)
                .lockDuration(cfg.lockDurationMs())
                .backoffStrategy(new ExponentialBackoffStrategy(500L, 2, 60_000L))
                .addInterceptor(new OidcTokenInterceptor(cfg))
                .build();

        if (cfg.topicEnabled(Topics.DORECZENIE_ELEKTRONICZNE)) {
            client.subscribe(Topics.DORECZENIE_ELEKTRONICZNE).handler(new ElectronicDeliveryHandler(cfg)).open();
        }
        if (cfg.topicEnabled(Topics.DORECZENIE_POCZTOWE)) {
            client.subscribe(Topics.DORECZENIE_POCZTOWE).handler(new PostalDeliveryHandler(cfg)).open();
        }
        if (cfg.topicEnabled(Topics.PRZEKAZANIE_MERYTORYCZNE)) {
            client.subscribe(Topics.PRZEKAZANIE_MERYTORYCZNE).handler(new MeritTransferHandler(cfg)).open();
        }
        LOG.info("eSkarga workers uruchomione: {} → {}", cfg.workerId(), cfg.engineRestUrl());
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop, "workers-shutdown"));
    }
}
