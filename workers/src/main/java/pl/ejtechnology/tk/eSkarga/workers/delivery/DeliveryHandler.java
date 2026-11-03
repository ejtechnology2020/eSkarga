package pl.ejtechnology.tk.eSkarga.workers.delivery;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.operaton.bpm.client.task.ExternalTask;

import pl.ejtechnology.tk.eSkarga.workers.AbstractIdempotentHandler;
import pl.ejtechnology.tk.eSkarga.workers.IdempotencyKey;
import pl.ejtechnology.tk.eSkarga.workers.JsonHttp;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/**
 * Doręczenia (T-043, FR-25, D7): dla sprawy z klucza biznesowego pobiera z API aplikacji pisma skierowane do wysyłki,
 * wysyła przez e-Doręczenia / ePUAP albo operatora pocztowego (Elektroniczny Nadawca) i raportuje nadanie.
 * Każda wysyłka ma klucz idempotencji z aplikacji ({@code DOR-…}); ponowienie z tym samym kluczem nie tworzy duplikatu.
 * [zaślepka] interfejsy operatorów – WireMock w {@code infra/docker/wiremock}.
 */
public final class DeliveryHandler extends AbstractIdempotentHandler {

    private final JsonHttp http;

    public DeliveryHandler(WorkerConfig cfg, JsonHttp http) {
        super(cfg);
        this.http = http;
    }

    @Override
    protected String topic() {
        return Topics.DORECZENIE_PISMA;
    }

    @Override
    protected Map<String, Object> handle(ExternalTask task, IdempotencyKey key) {
        String sygnatura = task.getBusinessKey();
        JsonHttp.Reply list = http.get(cfg.appApiUrl() + "/doreczenia/do-wysylki?sygnatura=" + java.net.URLEncoder.encode(sygnatura, java.nio.charset.StandardCharsets.UTF_8));
        if (!list.ok()) {
            throw new IllegalStateException("API aplikacji: HTTP " + list.status());
        }
        List<JsonObject> pending = list.json().getJsonArray("doreczenia").getValuesAs(JsonObject.class);
        if (pending.isEmpty()) {
            throw new IllegalStateException("Brak pism skierowanych do wysyłki w sprawie " + sygnatura + " – ponowienie");
        }
        for (JsonObject d : pending) {
            dispatch(d);
        }
        return Map.of();
    }

    private void dispatch(JsonObject d) {
        long id = d.getJsonNumber("id").longValue();
        String idem = d.getString("kluczIdempotencji");
        JsonHttp.Reply file = http.get(cfg.appApiUrl() + "/doreczenia/" + id + "/dokument");
        if (!file.ok()) {
            throw new IllegalStateException("Nie udało się pobrać dokumentu doręczenia " + id + ": HTTP " + file.status());
        }
        String channel = d.getString("kanal");
        String dispatchNo;
        if (channel.equals("EDORECZENIA") || channel.equals("EPUAP")) {
            JsonObject body = Json.createObjectBuilder().add("adresat", d.getString("adresat")).add("adresEDoreczen", d.getString("adresEDoreczen", ""))
                    .add("tytul", d.getString("tytul")).add("dokument", Base64.getEncoder().encodeToString(file.bytes())).build();
            JsonHttp.Reply r = http.postJson(cfg.externalBaseUrl() + "/edoreczenia/przesylki", body, Map.of("Idempotency-Key", idem));
            if (!r.ok()) {
                throw new IllegalStateException("e-Doręczenia: HTTP " + r.status());
            }
            dispatchNo = r.json().getString("identyfikator");
        } else {
            JsonObject body = Json.createObjectBuilder().add("adresat", d.getString("adresat")).add("adres", d.getString("adres", ""))
                    .add("rodzaj", "POLECONY_ZPO").add("tytul", d.getString("tytul")).build();
            JsonHttp.Reply r = http.postJson(cfg.externalBaseUrl() + "/poczta/przesylki", body, Map.of("Idempotency-Key", idem));
            if (!r.ok()) {
                throw new IllegalStateException("Operator pocztowy: HTTP " + r.status());
            }
            dispatchNo = r.json().getString("numerNadania");
        }
        JsonObject status = Json.createObjectBuilder().add("status", "WYSLANE").add("data", LocalDate.now().toString()).add("numerNadania", dispatchNo).build();
        JsonHttp.Reply s = http.postJson(cfg.appApiUrl() + "/doreczenia/" + id + "/status", status, Map.of());
        if (!s.ok()) {
            throw new IllegalStateException("Raport nadania odrzucony: HTTP " + s.status());
        }
        LOG.info("Doręczenie {} nadane przez {}: {}", id, channel, dispatchNo);
    }

    /** Mapowanie statusów operatorów na statusy aplikacji (FR-26). */
    public static String mapOperatorStatus(String operatorStatus) {
        return switch (operatorStatus == null ? "" : operatorStatus.toUpperCase()) {
            case "DORECZONA", "DELIVERED", "ODEBRANA" -> "DORECZONE";
            case "AWIZOWANA", "AWIZO", "NOTICE_LEFT" -> "AWIZO";
            case "ZWROT", "RETURNED", "NIEPODJETA" -> "ZWROT";
            case "NADANA", "WYSLANA", "SENT", "ACCEPTED" -> "WYSLANE";
            default -> "NIEZNANY";
        };
    }
}
