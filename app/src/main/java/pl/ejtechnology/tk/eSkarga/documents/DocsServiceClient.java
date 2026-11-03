package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import pl.ejtechnology.tk.eSkarga.common.security.ClientCredentialsTokenClient;
import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/**
 * Klient HTTP usługi dokumentów ({@code eskarga.docs.url}); token ID-users client credentials ({@code eskarga.docs.oidc.*}).
 * Usługa jest w sieci wewnętrznej TK – to nie jest „system zewnętrzny” w rozumieniu D7.
 */
@ApplicationScoped
public class DocsServiceClient implements DocsGateway {

    private String baseUrl;
    private ClientCredentialsTokenClient tokens;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @PostConstruct
    void init() {
        baseUrl = Settings.value("eskarga.docs.url", "http://localhost:8080/eskarga-docs/api");
        tokens = ClientCredentialsTokenClient.fromSettings("eskarga.docs.oidc");
    }

    public DocsServiceClient() {
    }

    public DocsServiceClient(String baseUrl, ClientCredentialsTokenClient tokens) {
        this.baseUrl = baseUrl;
        this.tokens = tokens;
    }

    @Override
    public byte[] renderLetter(String templateCode, String templateSource, Map<String, Object> model) {
        JsonObject body = Json.createObjectBuilder().add("kod", templateCode).add("zrodlo", templateSource).add("model", toJson(model)).build();
        return bytes(postJson("/pisma/render", body, "application/pdf"));
    }

    @Override
    public TemplateCheck validateTemplate(String templateCode, String templateSource) {
        JsonObject r = json(postJson("/pisma/szablony/waliduj", Json.createObjectBuilder().add("kod", templateCode).add("zrodlo", templateSource).build(), "application/json"));
        List<String> errors = r.getJsonArray("bledy").getValuesAs(JsonString.class).stream().map(JsonString::getString).toList();
        return new TemplateCheck(r.getBoolean("ok"), errors);
    }

    @Override
    public byte[] previewTemplate(String templateCode, String templateSource) {
        return bytes(postJson("/pisma/szablony/podglad", Json.createObjectBuilder().add("kod", templateCode).add("zrodlo", templateSource).build(), "application/pdf"));
    }

    @Override
    public DataToSign dataToSign(byte[] pdf, List<String> certificateChainBase64, String signatureAlgorithm, String reason) {
        JsonArrayBuilder certs = Json.createArrayBuilder();
        certificateChainBase64.forEach(certs::add);
        JsonObject r = json(postJson("/podpis/dane-do-podpisu", Json.createObjectBuilder().add("pdf", Base64.getEncoder().encodeToString(pdf))
                .add("certyfikaty", certs).add("algorytm", signatureAlgorithm).add("powod", reason).build(), "application/json"));
        return new DataToSign(r.getString("sesja"), r.getString("skrot"), r.getString("algorytmSkrotu"), r.getString("algorytmPodpisu"));
    }

    @Override
    public byte[] signDocument(String sessionId, String signatureValueBase64) {
        return bytes(postJson("/podpis/zloz", Json.createObjectBuilder().add("sesja", sessionId).add("podpis", signatureValueBase64).build(), "application/pdf"));
    }

    @Override
    public Processed process(byte[] pdf, boolean ocr) {
        JsonObject r = json(post("/dokumenty/przetworz?ocr=" + ocr, pdf, "application/pdf", "application/json"));
        JsonObject report = r.getJsonObject("raportPodpisu");
        return new Processed(Base64.getDecoder().decode(r.getString("pdf")), r.getString("sha256"), r.getBoolean("pdfaZgodny"), r.getBoolean("ocrWykonany"),
                report.toString(), report.getBoolean("podpisany", false), report.getBoolean("wszystkieWazne", true));
    }

    @Override
    public byte[] watermark(byte[] pdf, String text) {
        return bytes(post("/dokumenty/znak-wodny?tekst=" + URLEncoder.encode(text, StandardCharsets.UTF_8), pdf, "application/pdf", "application/pdf"));
    }

    @Override
    public Map<String, String> defaultTemplates() {
        HttpRequest req = base("/pisma/szablony/domyslne", "application/json").GET().build();
        JsonObject r = json(send(req));
        Map<String, String> out = new LinkedHashMap<>();
        r.getJsonArray("szablony").getValuesAs(JsonObject.class).forEach(t -> out.put(t.getString("kod"), t.getString("zrodlo")));
        return out;
    }

    private HttpResponse<byte[]> postJson(String path, JsonObject body, String accept) {
        return post(path, body.toString().getBytes(StandardCharsets.UTF_8), "application/json", accept);
    }

    private HttpResponse<byte[]> post(String path, byte[] body, String contentType, String accept) {
        return send(base(path, accept).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    private HttpRequest.Builder base(String path, String accept) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(120)).header("Accept", accept);
        if (tokens != null && tokens.configured()) {
            b.header("Authorization", "Bearer " + tokens.token());
        }
        return b;
    }

    private HttpResponse<byte[]> send(HttpRequest req) {
        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                String msg = resp.statusCode() + "";
                String ct = resp.headers().firstValue("Content-Type").orElse("");
                if (ct.contains("json") && resp.body().length > 0) {
                    JsonObject err = Json.createReader(new StringReader(new String(resp.body(), StandardCharsets.UTF_8))).readObject();
                    msg += " " + err.getString("komunikat", "");
                    if (err.containsKey("naruszenia")) {
                        msg += " " + err.getJsonArray("naruszenia");
                    }
                }
                throw new IllegalStateException("Usługa dokumentów: HTTP " + msg);
            }
            return resp;
        } catch (IOException e) {
            throw new IllegalStateException("Usługa dokumentów niedostępna: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(HttpResponse<byte[]> r) {
        return r.body();
    }

    private static JsonObject json(HttpResponse<byte[]> r) {
        return Json.createReader(new StringReader(new String(r.body(), StandardCharsets.UTF_8))).readObject();
    }

    @SuppressWarnings("unchecked")
    static JsonObjectBuilder toJson(Map<String, Object> model) {
        JsonObjectBuilder b = Json.createObjectBuilder();
        for (Map.Entry<String, Object> e : model.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                b.addNull(e.getKey());
            } else if (v instanceof String s) {
                b.add(e.getKey(), s);
            } else if (v instanceof Boolean bo) {
                b.add(e.getKey(), bo);
            } else if (v instanceof Integer || v instanceof Long) {
                b.add(e.getKey(), ((Number) v).longValue());
            } else if (v instanceof Number n) {
                b.add(e.getKey(), n.doubleValue());
            } else if (v instanceof Map<?, ?> m) {
                b.add(e.getKey(), toJson((Map<String, Object>) m));
            } else if (v instanceof List<?> l) {
                JsonArrayBuilder arr = Json.createArrayBuilder();
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m2) {
                        arr.add(toJson((Map<String, Object>) m2));
                    } else {
                        arr.add(String.valueOf(o));
                    }
                }
                b.add(e.getKey(), arr);
            } else {
                b.add(e.getKey(), String.valueOf(v)); // tylko proste typy (D13) – encje i beany nigdy nie trafiają do modelu
            }
        }
        return b;
    }

    static List<String> strings(JsonValue v) {
        List<String> out = new ArrayList<>();
        ((jakarta.json.JsonArray) v).getValuesAs(JsonString.class).forEach(s -> out.add(s.getString()));
        return out;
    }
}
