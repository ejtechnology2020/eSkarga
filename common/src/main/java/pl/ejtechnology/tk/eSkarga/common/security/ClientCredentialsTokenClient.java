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
    private final java.security.PrivateKey assertionKey;
    private final String assertionKid;
    private final HttpClient http;
    private volatile String cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokenClient(String tokenUrl, String clientId, String clientSecret, String scope) {
        this(tokenUrl, clientId, clientSecret, scope, null, null);
    }

    /** Uwierzytelnienie {@code private_key_jwt} (zalecane przez ID-users – rotacja klucza bez przerwy): klucz zamiast sekretu. */
    public ClientCredentialsTokenClient(String tokenUrl, String clientId, String clientSecret, String scope, java.security.PrivateKey assertionKey, String assertionKid) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.assertionKey = assertionKey;
        this.assertionKid = assertionKid;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * Konfiguracja {@code <prefix>.tokenUrl}, {@code .clientId}, {@code .scope} oraz {@code .clientSecret} albo (zalecane, kontrakt ID-users)
     * {@code private_key_jwt}: {@code .privateKeyPem} (klucz PKCS#8 w PEM, jak zapisuje narzędzie rejestracji ID-users {@code aplikacje:eskarga};
     * {@code kid} z {@code .keyId} albo z pliku {@code <klient>.kid} obok klucza) lub {@code .keyStore} (PKCS#12), {@code .keyStorePassword},
     * {@code .keyAlias}.
     */
    public static ClientCredentialsTokenClient fromSettings(String prefix) {
        String ks = Settings.value(prefix + ".keyStore", "");
        String pem = Settings.value(prefix + ".privateKeyPem", "");
        java.security.PrivateKey key = null;
        String kid = null;
        if (!pem.isBlank()) {
            java.nio.file.Path plik = java.nio.file.Path.of(pem);
            key = privateKeyFromPem(plik);
            kid = Settings.value(prefix + ".keyId", keyIdNextTo(plik));
        } else if (!ks.isBlank()) {
            try {
                java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
                char[] pwd = Settings.value(prefix + ".keyStorePassword", "").toCharArray();
                try (var in = new java.io.FileInputStream(ks)) {
                    store.load(in, pwd);
                }
                String alias = Settings.value(prefix + ".keyAlias", store.aliases().nextElement());
                key = (java.security.PrivateKey) store.getKey(alias, pwd);
                kid = alias;
            } catch (java.security.GeneralSecurityException | IOException e) {
                throw new IllegalStateException("Nie udało się wczytać klucza klienta " + prefix, e);
            }
        }
        return new ClientCredentialsTokenClient(Settings.value(prefix + ".tokenUrl", ""), Settings.value(prefix + ".clientId", ""),
                Settings.value(prefix + ".clientSecret", ""), Settings.value(prefix + ".scope", ""), key, kid);
    }

    /** Klucz prywatny RSA z pliku PEM (PKCS#8, {@code -----BEGIN PRIVATE KEY-----}) – format narzędzia rejestracji ID-users. */
    public static java.security.PrivateKey privateKeyFromPem(java.nio.file.Path plik) {
        try {
            String tekst = java.nio.file.Files.readString(plik, StandardCharsets.US_ASCII);
            if (!tekst.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalStateException("Plik " + plik.getFileName() + " nie zawiera klucza PKCS#8 (-----BEGIN PRIVATE KEY-----)");
            }
            String b64 = tekst.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = java.util.Base64.getMimeDecoder().decode(b64);
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (IOException | java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Nie udało się wczytać klucza prywatnego " + plik.getFileName(), e);
        }
    }

    /** {@code kid} z pliku {@code <nazwa>.kid} obok {@code <nazwa>.key.pem} (narzędzie ID-users); null, gdy brak – Keycloak dobiera klucz po algorytmie. */
    public static String keyIdNextTo(java.nio.file.Path pem) {
        String nazwa = pem.getFileName().toString();
        String baza = nazwa.endsWith(".key.pem") ? nazwa.substring(0, nazwa.length() - ".key.pem".length()) : nazwa.replaceFirst("\\.[^.]+$", "");
        java.nio.file.Path kid = pem.resolveSibling(baza + ".kid");
        try {
            return java.nio.file.Files.isRegularFile(kid) ? java.nio.file.Files.readString(kid, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public boolean usesPrivateKeyJwt() {
        return assertionKey != null;
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
        StringBuilder form = new StringBuilder("grant_type=client_credentials&client_id=").append(enc(clientId));
        if (assertionKey != null) {
            form.append("&client_assertion_type=").append(enc("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"))
                    .append("&client_assertion=").append(enc(clientAssertion()));
        } else {
            form.append("&client_secret=").append(enc(clientSecret));
        }
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

    /** JWT RS256: iss = sub = client_id, aud = adres tokenu (Keycloak), ważność 60 s, jti losowe. */
    String clientAssertion() {
        try {
            long now = Instant.now().getEpochSecond();
            String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"" + (assertionKid == null ? "" : ",\"kid\":\"" + assertionKid + "\"") + "}");
            String payload = b64("{\"iss\":\"" + clientId + "\",\"sub\":\"" + clientId + "\",\"aud\":\"" + tokenUrl + "\",\"jti\":\"" + java.util.UUID.randomUUID()
                    + "\",\"iat\":" + now + ",\"exp\":" + (now + 60) + "}");
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(assertionKey);
            sig.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Nie udało się podpisać client_assertion", e);
        }
    }

    private static String b64(String s) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
