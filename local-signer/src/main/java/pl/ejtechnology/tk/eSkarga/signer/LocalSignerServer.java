package pl.ejtechnology.tk.eSkarga.signer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Kanał przeglądarka ↔ aplikacja lokalna (ADR 0008): HTTP tylko na 127.0.0.1, CORS wyłącznie dla adresu eSkargi.
 * {@code GET /status}, {@code GET /certyfikaty} (łańcuch DER base64), {@code POST /podpisz} {@code {"skrot": b64, "algorytmSkrotu": "SHA-256"}}
 * → {@code {"podpis": b64, "algorytm": "RSA_SHA256"}}. Żaden dokument nie trafia do tej aplikacji – tylko skrót (D10).
 */
public final class LocalSignerServer {

    private static final Pattern DIGEST = Pattern.compile("\"skrot\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");

    private final DigestSigner signer;
    private final List<String> allowedOrigins;
    private HttpServer server;

    public LocalSignerServer(DigestSigner signer, List<String> allowedOrigins) {
        this.signer = signer;
        this.allowedOrigins = allowedOrigins;
    }

    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/status", this::status);
        server.createContext("/certyfikaty", this::certificates);
        server.createContext("/podpisz", this::sign);
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private boolean cors(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            if (!allowedOrigins.contains(origin)) {
                write(ex, 403, "{\"komunikat\":\"niedozwolone pochodzenie\"}");
                return false;
            }
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().add("Vary", "Origin");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            ex.getResponseHeaders().add("Access-Control-Max-Age", "600");
        }
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return false;
        }
        return true;
    }

    private void status(HttpExchange ex) throws IOException {
        if (!cors(ex)) {
            return;
        }
        try {
            X509Certificate c = signer.certificateChain().get(0);
            boolean valid = true;
            try {
                c.checkValidity();
            } catch (GeneralSecurityException e) {
                valid = false;
            }
            write(ex, 200, "{\"gotowa\":true,\"karta\":true,\"podmiot\":" + json(c.getSubjectX500Principal().getName()) + ",\"waznyDo\":" + json(c.getNotAfter().toInstant().toString())
                    + ",\"certyfikatWazny\":" + valid + ",\"algorytm\":" + json(signer.signatureAlgorithm()) + ",\"kwalifikowany\":false}");
        } catch (GeneralSecurityException e) {
            write(ex, 503, "{\"gotowa\":false,\"karta\":false,\"komunikat\":" + json(e.getMessage()) + "}");
        }
    }

    private void certificates(HttpExchange ex) throws IOException {
        if (!cors(ex)) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("{\"lancuch\":[");
            boolean first = true;
            for (X509Certificate c : signer.certificateChain()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(Base64.getEncoder().encodeToString(c.getEncoded())).append('"');
            }
            write(ex, 200, sb.append("],\"algorytm\":").append(json(signer.signatureAlgorithm())).append('}').toString());
        } catch (GeneralSecurityException e) {
            write(ex, 503, "{\"komunikat\":" + json(e.getMessage()) + "}");
        }
    }

    private void sign(HttpExchange ex) throws IOException {
        if (!cors(ex)) {
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            write(ex, 405, "{\"komunikat\":\"POST\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = DIGEST.matcher(body);
        if (!m.find()) {
            write(ex, 400, "{\"komunikat\":\"brak skrótu\"}");
            return;
        }
        try {
            byte[] digest = Base64.getDecoder().decode(m.group(1));
            byte[] signature = signer.signSha256Digest(digest);
            write(ex, 200, "{\"podpis\":\"" + Base64.getEncoder().encodeToString(signature) + "\",\"algorytm\":" + json(signer.signatureAlgorithm()) + "}");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            write(ex, 422, "{\"komunikat\":" + json("Podpis nie powiódł się: " + e.getMessage()) + "}");
        }
    }

    static void write(HttpExchange ex, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
