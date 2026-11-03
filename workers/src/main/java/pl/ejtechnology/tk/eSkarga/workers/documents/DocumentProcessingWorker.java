package pl.ejtechnology.tk.eSkarga.workers.documents;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.ejtechnology.tk.eSkarga.workers.JsonHttp;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/**
 * Przetwarzanie nowych dokumentów akt (T-031, NFR-11): co {@code documentPollSeconds} pobiera z API aplikacji dokumenty
 * bez PDF/A i raportu podpisu, przepuszcza je przez usługę dokumentów (odszyfrowanie, walidacja podpisów, OCR, PDF/A)
 * i zapisuje wynik. Idempotentne: ten sam dokument daje ten sam wynik, a aplikacja ignoruje powtórzony wynik.
 */
public final class DocumentProcessingWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final WorkerConfig cfg;
    private final JsonHttp http;
    private ScheduledExecutorService executor;

    public DocumentProcessingWorker(WorkerConfig cfg, JsonHttp http) {
        this.cfg = cfg;
        this.http = http;
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "dokument-przetworz"));
        executor.scheduleWithFixedDelay(() -> {
            try {
                runOnce();
            } catch (RuntimeException e) {
                LOG.warn("Cykl przetwarzania dokumentów nieudany: {}", e.getMessage());
            }
        }, 5, cfg.documentPollSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Jeden cykl; zwraca liczbę przetworzonych dokumentów. */
    public int runOnce() {
        JsonHttp.Reply list = http.get(cfg.appApiUrl() + "/dokumenty/do-przetworzenia");
        if (!list.ok()) {
            throw new IllegalStateException("API aplikacji: HTTP " + list.status());
        }
        List<JsonObject> docs = list.json().getJsonArray("dokumenty").getValuesAs(JsonObject.class);
        int done = 0;
        for (JsonObject d : docs) {
            long id = d.getJsonNumber("id").longValue();
            try {
                process(id);
                done++;
            } catch (RuntimeException e) {
                LOG.warn("Dokument {} nie został przetworzony: {}", id, e.getMessage());
            }
        }
        return done;
    }

    private void process(long id) {
        JsonHttp.Reply file = http.get(cfg.appApiUrl() + "/dokumenty/" + id + "/plik");
        if (!file.ok()) {
            throw new IllegalStateException("plik: HTTP " + file.status());
        }
        JsonHttp.Reply result = http.postBytes(cfg.docsApiUrl() + "/dokumenty/przetworz?ocr=true", file.bytes(), "application/pdf");
        if (!result.ok() || result.json() == null) {
            throw new IllegalStateException("usługa dokumentów: HTTP " + result.status());
        }
        JsonObjectBuilder b = Json.createObjectBuilder();
        for (Map.Entry<String, jakarta.json.JsonValue> e : result.json().entrySet()) {
            b.add(e.getKey(), e.getValue());
        }
        JsonHttp.Reply saved = http.postJson(cfg.appApiUrl() + "/dokumenty/" + id + "/wynik", b.build(), Map.of());
        if (!saved.ok()) {
            throw new IllegalStateException("zapis wyniku: HTTP " + saved.status());
        }
        LOG.info("Dokument {} przetworzony (PDF/A: {}, OCR: {})", id, result.json().getBoolean("pdfaZgodny", false), result.json().getBoolean("ocrWykonany", false));
    }
}
