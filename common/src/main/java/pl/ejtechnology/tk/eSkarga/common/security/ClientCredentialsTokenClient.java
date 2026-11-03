package pl.ejtechnology.tk.eSkarga.common.security;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Token dostępu ID-users dla usług technicznych (client credentials, T-042, D7): aplikacja → usługa dokumentów,
 * workery → REST silnika i API aplikacji. Token jest buforowany do 30 s przed wygaśnięciem; sekret klienta pochodzi
 * ze zmiennej środowiskowej. Adres punktu końcowego jest konfigurowalny – kontrakt ID-users nie jest zaszyty.
 */
public final class ClientCredentialsTokenClient {

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final HttpClient http;
    private volatile String cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokenClient(String tokenUrl, String clientId, String clientSecret, String scope) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Konfiguracja {@code <prefix>.tokenUrl}, {@code <prefix>.clientId}, {@code <prefix>.clientSecret}, {@code <prefix>.scope}. */
    public static ClientCredentialsTokenClient fromSettings(String prefix) {
        return new ClientCredentialsTokenClient(Settings.value(prefix + ".tokenUrl", ""), Settings.value(prefix + ".clientId", ""),
                Settings.value(prefix + ".clientSecret", ""), Settings.value(prefix + ".scope", ""));
    }

    public boolean configured() {
        return tokenUrl != null && !tokenUrl.isBlank();
    }

    public String token() {
        String t = cached;
        if (t != null && Instant.now().plusSeconds(30).isBefore(expiresAt)) {
            return t;
        }
        synchronized (this) {
            if (cached != null && Instant.now().plusSeconds(30).isBefore(expiresAt)) {
                return cached;
            }
            fetch();
            return cached;
        }
    }

    private void fetch() {
        StringBuilder form = new StringBuilder("grant_type=client_credentials&client_id=").append(enc(clientId)).append("&client_secret=").append(enc(clientSecret));
        if (scope != null && !scope.isBlank()) {
            form.append("&scope=").append(enc(scope));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("ID-users odrzucił żądanie tokenu: HTTP " + resp.statusCode());
            }
            JsonObject json = Json.createReader(new StringReader(resp.body())).readObject();
            String token = json.getString("access_token", null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Odpowiedź ID-users bez access_token");
            }
            long expiresIn = json.containsKey("expires_in") ? json.getJsonNumber("expires_in").longValue() : 300;
            cached = token;
            expiresAt = Instant.now().plusSeconds(expiresIn);
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się pobrać tokenu z " + tokenUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
