package pl.ejtechnology.tk.eSkarga.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T-042: client credentials – treść żądania, bufor tokenu do wygaśnięcia, błąd przy odmowie. */
class ClientCredentialsTokenClientTest {

    static HttpServer server;
    static final AtomicInteger calls = new AtomicInteger();
    static final List<String> bodies = new ArrayList<>();
    static volatile int status = 200;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            calls.incrementAndGet();
            byte[] in = ex.getRequestBody().readAllBytes();
            bodies.add(new String(in, StandardCharsets.UTF_8));
            String json = "{\"access_token\":\"tok-" + calls.get() + "\",\"token_type\":\"Bearer\",\"expires_in\":120}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    @Test
    void pobieraIBuforujeToken() {
        ClientCredentialsTokenClient c = new ClientCredentialsTokenClient(url(), "eskarga-workers", "sekret ze środowiska", "eskarga-api");
        assertThat(c.configured()).isTrue();
        int before = calls.get();
        String t1 = c.token();
        String t2 = c.token();
        assertThat(t1).isEqualTo(t2).startsWith("tok-");
        assertThat(calls.get()).as("drugie wywołanie z bufora").isEqualTo(before + 1);
        assertThat(bodies.get(bodies.size() - 1)).contains("grant_type=client_credentials").contains("client_id=eskarga-workers").contains("scope=eskarga-api")
                .contains("client_secret=sekret+ze+%C5%9Brodowiska");
    }

    @Test
    void privateKeyJwtZamiastSekretu() throws Exception {
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        java.security.KeyPair kp = g.generateKeyPair();
        ClientCredentialsTokenClient c = new ClientCredentialsTokenClient(url(), "eskarga-workers", null, "eskarga", kp.getPrivate(), "klucz-2026");
        assertThat(c.usesPrivateKeyJwt()).isTrue();
        assertThat(c.token()).startsWith("tok-");
        String body = bodies.get(bodies.size() - 1);
        assertThat(body).contains("client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer").contains("client_assertion=").doesNotContain("client_secret");
        String assertion = java.net.URLDecoder.decode(body.replaceAll(".*client_assertion=([^&]*).*", "$1"), StandardCharsets.UTF_8);
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\":\"eskarga-workers\"").contains("\"aud\":\"" + url() + "\"").contains("\"jti\"");
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(kp.getPublic());
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(sig.verify(java.util.Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void odmowaDostawcyToBlad() {
        status = 401;
        try {
            ClientCredentialsTokenClient c = new ClientCredentialsTokenClient(url(), "x", "y", null);
            assertThatThrownBy(c::token).isInstanceOf(IllegalStateException.class).hasMessageContaining("401");
        } finally {
            status = 200;
        }
        assertThat(new ClientCredentialsTokenClient("", "x", "y", null).configured()).isFalse();
    }

    @Test
    void kluczPemNarzedziaIdUsersIKidObok(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.security.KeyPair kp = java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        java.nio.file.Path plik = dir.resolve("eskarga-workers.key.pem");
        java.nio.file.Files.writeString(plik, pem);
        java.nio.file.Files.writeString(dir.resolve("eskarga-workers.kid"), "eskarga-workers-2026-11-05\n");
        assertThat(ClientCredentialsTokenClient.privateKeyFromPem(plik)).isEqualTo(kp.getPrivate());
        assertThat(ClientCredentialsTokenClient.keyIdNextTo(plik)).isEqualTo("eskarga-workers-2026-11-05");
        assertThat(ClientCredentialsTokenClient.keyIdNextTo(dir.resolve("inny.key.pem"))).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ClientCredentialsTokenClient.privateKeyFromPem(dir.resolve("eskarga-workers.kid")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PKCS#8");
    }

    @Test
    void asercjaZPublicznymAudienceIKid() throws Exception {
        java.security.KeyPair kp = java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String jwt = ClientCredentialsTokenClient.signedAssertion("eskarga", "http://localhost:8180/realms/id-users/protocol/openid-connect/token", kp.getPrivate(), "eskarga-2026-11-04");
        String[] czesci = jwt.split("\\.");
        String naglowek = new String(java.util.Base64.getUrlDecoder().decode(czesci[0]), java.nio.charset.StandardCharsets.UTF_8);
        String tresc = new String(java.util.Base64.getUrlDecoder().decode(czesci[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(naglowek).contains("\"kid\":\"eskarga-2026-11-04\"").contains("\"alg\":\"RS256\"");
        assertThat(tresc).contains("\"aud\":\"http://localhost:8180/realms/id-users/protocol/openid-connect/token\"").contains("\"iss\":\"eskarga\"");
        java.security.Signature weryfikacja = java.security.Signature.getInstance("SHA256withRSA");
        weryfikacja.initVerify(kp.getPublic());
        weryfikacja.update((czesci[0] + "." + czesci[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(weryfikacja.verify(java.util.Base64.getUrlDecoder().decode(czesci[2]))).isTrue();
        ClientCredentialsTokenClient c = new ClientCredentialsTokenClient("http://host.docker.internal:8180/t", "eskarga", null, null, kp.getPrivate(), null,
                "http://localhost:8180/realms/id-users");
        String tresc2 = new String(java.util.Base64.getUrlDecoder().decode(c.clientAssertion().split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(tresc2).contains("\"aud\":\"http://localhost:8180/realms/id-users\"");
    }
}
