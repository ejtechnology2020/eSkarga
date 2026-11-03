package pl.ejtechnology.tk.eSkarga.common.security;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;


/**
 * Weryfikator JWT (RS256) bez zewnętrznych bibliotek: podpis kluczem z JWKS wydawcy (ID-users), {@code iss}, {@code aud},
 * {@code exp} z marginesem 60 s. Nazwa claimu ról jest konfigurowalna ({@code eskarga.oidc.rolesClaim}), tak jak w logowaniu
 * użytkowników – kontrakt ID-users nie jest zaszyty na sztywno. Weryfikacja podpisu nigdy nie jest wyłączana.
 */
public class JwtRs256Verifier implements TokenVerifier {

    public static final String AUDIENCE_PROPERTY = "eskarga.workers.audience";
    public static final String JWKS_PROPERTY = "eskarga.workers.jwksUri";
    private static final Duration JWKS_TTL = Duration.ofMinutes(10);

    private final String issuer;
    private final String audience;
    private final String rolesClaim;
    private final Supplier<Map<String, RSAPublicKey>> keySource;
    private volatile Map<String, RSAPublicKey> keys = Map.of();
    private volatile Instant keysLoadedAt = Instant.EPOCH;

    /** Konfiguracja z właściwości; klucze pobierane z JWKS wydawcy. */
    public static JwtRs256Verifier fromSettings() {
        String issuer = Settings.value("eskarga.oidc.issuer", "");
        String jwks = Settings.value(JWKS_PROPERTY, issuer.endsWith("/") ? issuer + "protocol/openid-connect/certs" : issuer + "/.well-known/jwks.json");
        return new JwtRs256Verifier(issuer, Settings.value(AUDIENCE_PROPERTY, "eskarga-api"), Settings.value("eskarga.oidc.rolesClaim", "roles"),
                () -> fetchJwks(jwks));
    }

    public JwtRs256Verifier(String issuer, String audience, String rolesClaim, Supplier<Map<String, RSAPublicKey>> keySource) {
        this.issuer = issuer;
        this.audience = audience;
        this.rolesClaim = rolesClaim;
        this.keySource = keySource;
    }

    /** Weryfikator tokenów kierowanych do aplikacji jako klienta OIDC (np. {@code logout_token} back-channel). */
    public static JwtRs256Verifier forClient() {
        String issuer = Settings.value("eskarga.oidc.issuer", "");
        String jwks = Settings.value(JWKS_PROPERTY, issuer.endsWith("/") ? issuer + "protocol/openid-connect/certs" : issuer + "/.well-known/jwks.json");
        return new JwtRs256Verifier(issuer, Settings.value("eskarga.oidc.clientId", "eskarga"), Settings.value("eskarga.oidc.rolesClaim", "roles"),
                () -> fetchJwks(jwks));
    }

    @Override
    public Optional<Claims> verify(String token) {
        return verifyPayload(token).map(payload -> new Claims(payload.getString("sub", null), issuer, roles(payload),
                payload.containsKey("exp") ? Instant.ofEpochSecond(payload.getJsonNumber("exp").longValue()) : Instant.MAX));
    }

    /** Pełny ładunek tokenu po sprawdzeniu podpisu, wydawcy, odbiorcy i ważności ({@code exp} opcjonalne tylko dla tokenów zdarzeń). */
    public Optional<JsonObject> verifyPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            JsonObject header = json(parts[0]);
            if (!"RS256".equals(header.getString("alg", null))) {
                return Optional.empty();
            }
            String kid = header.getString("kid", null);
            RSAPublicKey key = key(kid);
            if (key == null) {
                return Optional.empty();
            }
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                return Optional.empty();
            }
            JsonObject payload = json(parts[1]);
            if (!issuer.equals(payload.getString("iss", null))) {
                return Optional.empty();
            }
            if (!audienceMatches(payload.get("aud"))) {
                return Optional.empty();
            }
            if (payload.containsKey("exp")) {
                Instant exp = Instant.ofEpochSecond(payload.getJsonNumber("exp").longValue());
                if (exp.plusSeconds(60).isBefore(Instant.now())) {
                    return Optional.empty();
                }
            }
            if (payload.containsKey("iat")) {
                Instant iat = Instant.ofEpochSecond(payload.getJsonNumber("iat").longValue());
                if (iat.isAfter(Instant.now().plusSeconds(120))) {
                    return Optional.empty();
                }
            }
            return Optional.of(payload);
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    private boolean audienceMatches(JsonValue aud) {
        if (aud == null) {
            return false;
        }
        if (aud.getValueType() == JsonValue.ValueType.STRING) {
            return audience.equals(((JsonString) aud).getString());
        }
        if (aud.getValueType() == JsonValue.ValueType.ARRAY) {
            return ((JsonArray) aud).getValuesAs(JsonString.class).stream().anyMatch(s -> audience.equals(s.getString()));
        }
        return false;
    }

    private Set<String> roles(JsonObject payload) {
        Set<String> out = new HashSet<>();
        JsonValue v = payload;
        for (String part : rolesClaim.split("\\.")) {
            if (v == null || v.getValueType() != JsonValue.ValueType.OBJECT) {
                return out;
            }
            v = ((JsonObject) v).get(part);
        }
        if (v == null) {
            return out;
        }
        if (v.getValueType() == JsonValue.ValueType.ARRAY) {
            ((JsonArray) v).getValuesAs(JsonString.class).forEach(s -> out.add(s.getString()));
        } else if (v.getValueType() == JsonValue.ValueType.STRING) {
            for (String s : ((JsonString) v).getString().split("[ ,]")) {
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private RSAPublicKey key(String kid) {
        if (keysLoadedAt.plus(JWKS_TTL).isBefore(Instant.now()) || (kid != null && !keys.containsKey(kid))) {
            keys = keySource.get();
            keysLoadedAt = Instant.now();
        }
        if (kid != null) {
            return keys.get(kid);
        }
        return keys.size() == 1 ? keys.values().iterator().next() : null;
    }

    public static JsonObject json(String base64Url) {
        return Json.createReader(new StringReader(new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8))).readObject();
    }

    /** Pobiera klucze RSA z dokumentu JWKS (tylko {@code kty=RSA}, {@code use=sig} albo bez {@code use}). */
    public static Map<String, RSAPublicKey> parseJwks(String jwksJson) {
        Map<String, RSAPublicKey> out = new HashMap<>();
        JsonObject doc = Json.createReader(new StringReader(jwksJson)).readObject();
        for (JsonObject k : doc.getJsonArray("keys").getValuesAs(JsonObject.class)) {
            if (!"RSA".equals(k.getString("kty", null)) || (k.containsKey("use") && !"sig".equals(k.getString("use")))) {
                continue;
            }
            try {
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.getString("n")));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.getString("e")));
                out.put(k.getString("kid", "default"), (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            } catch (java.security.GeneralSecurityException ex) {
                throw new IllegalStateException("Niepoprawny klucz w JWKS", ex);
            }
        }
        return out;
    }

    static Map<String, RSAPublicKey> fetchJwks(String uri) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("JWKS " + uri + " → HTTP " + resp.statusCode());
            }
            return parseJwks(resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się pobrać JWKS z " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
