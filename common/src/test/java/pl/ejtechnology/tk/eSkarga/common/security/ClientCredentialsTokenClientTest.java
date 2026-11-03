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
}
