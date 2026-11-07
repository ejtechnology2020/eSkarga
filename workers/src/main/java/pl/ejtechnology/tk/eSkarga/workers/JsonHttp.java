package pl.ejtechnology.tk.eSkarga.workers;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import pl.ejtechnology.tk.eSkarga.common.security.ClientCredentialsTokenClient;

/** Mały klient HTTP/JSON dla workerów: token ID-users w nagłówku, nagłówek {@code Idempotency-Key} dla wysyłek (D7). */
public final class JsonHttp {

    /** Odpowiedź: kod i treść JSON (pusta przy 204). */
    public record Reply(int status, JsonObject json, byte[] bytes) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ClientCredentialsTokenClient tokens;

    public JsonHttp(ClientCredentialsTokenClient tokens) {
        this.tokens = tokens;
    }

    public Reply get(String url) {
        return send(base(url).GET(), true);
    }

    public Reply postJson(String url, JsonObject body, Map<String, String> headers) {
        HttpRequest.Builder b = base(url).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b, true);
    }

    public Reply postBytes(String url, byte[] body, String contentType) {
        return send(base(url).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)), true);
    }

    private HttpRequest.Builder base(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Accept", "application/json, application/pdf, application/octet-stream");
        if (tokens != null && tokens.configured()) {
            b.header("Authorization", "Bearer " + tokens.token());
        }
        return b;
    }

    private Reply send(HttpRequest.Builder b, boolean parse) {
        try {
            HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            JsonObject json = null;
            if (parse && ct.contains("json") && resp.body().length > 0) {
                json = Json.createReader(new StringReader(new String(resp.body(), StandardCharsets.UTF_8))).readObject();
            }
            return new Reply(resp.statusCode(), json, resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("Błąd połączenia: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
