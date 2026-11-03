package pl.ejtechnology.tk.eSkarga.signer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** D10 / ADR 0008: podpis skrótu (RSA PKCS#1 v1.5 z DigestInfo i ECDSA) oraz kanał HTTP z CORS tylko dla eSkargi. */
class LocalSignerTest {

    static final char[] PIN = "123456".toCharArray();
    static LocalSignerServer server;
    static int port;
    static KeyPair rsa;

    static KeyStore keyStore(String alg, String alias, KeyPair[] holder) throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance(alg);
        if (alg.equals("RSA")) {
            g.initialize(2048);
        } else {
            g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        }
        KeyPair kp = g.generateKeyPair();
        holder[0] = kp;
        X500Name name = new X500Name("CN=sedzia TK Piotr Fikcyjny (YubiKey test), O=TK, C=PL");
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                new Date(System.currentTimeMillis() - 86_400_000L), new Date(System.currentTimeMillis() + 365L * 86_400_000L), name, kp.getPublic())
                .build(new JcaContentSignerBuilder(alg.equals("RSA") ? "SHA256withRSA" : "SHA256withECDSA").build(kp.getPrivate())));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, PIN);
        ks.setKeyEntry(alias, kp.getPrivate(), PIN, new java.security.cert.Certificate[]{cert});
        return ks;
    }

    @BeforeAll
    static void start() throws Exception {
        KeyPair[] h = new KeyPair[1];
        KeyStore ks = keyStore("RSA", "podpis", h);
        rsa = h[0];
        server = new LocalSignerServer(new DigestSigner(ks, PIN, null), List.of("http://localhost:8080"));
        port = server.start(0);
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @Test
    void podpisRsaSkrotuJestPoprawnymPkcs1() throws Exception {
        byte[] data = "dane do podpisu".getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> status = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/status")).header("Origin", "http://localhost:8080").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"gotowa\":true").contains("\"kwalifikowany\":false").contains("RSA_SHA256");
        assertThat(status.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:8080");

        HttpResponse<String> certs = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/certyfikaty")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(certs.body()).contains("\"lancuch\":[\"MII");

        HttpResponse<String> signed = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/podpisz")).header("Origin", "http://localhost:8080")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"skrot\":\"" + Base64.getEncoder().encodeToString(digest) + "\",\"algorytmSkrotu\":\"SHA-256\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(signed.statusCode()).isEqualTo(200);
        String sigB64 = signed.body().replaceAll(".*\"podpis\":\"([^\"]+)\".*", "$1");
        Signature verify = Signature.getInstance("SHA256withRSA");
        verify.initVerify(rsa.getPublic());
        verify.update(data);
        assertThat(verify.verify(Base64.getDecoder().decode(sigB64))).as("podpis skrótu równoważny SHA256withRSA nad danymi (DSS krok 2)").isTrue();
    }

    @Test
    void obcePochodzenieIZlySkrotSaOdrzucane() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> forbidden = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/status")).header("Origin", "https://zly.example").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(forbidden.statusCode()).isEqualTo(403);
        HttpResponse<String> bad = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/podpisz")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"skrot\":\"AQID\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(bad.statusCode()).isEqualTo(422);
        HttpResponse<String> preflight = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/podpisz")).header("Origin", "http://localhost:8080")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(204);
    }

    @Test
    void ecdsaDajePodpisDerWeryfikowalnyJakoSha256withEcdsa() throws Exception {
        KeyPair[] h = new KeyPair[1];
        DigestSigner ec = new DigestSigner(keyStore("EC", "ec", h), PIN, null);
        assertThat(ec.signatureAlgorithm()).isEqualTo("ECDSA_SHA256");
        byte[] data = "postanowienie".getBytes(StandardCharsets.UTF_8);
        byte[] sig = ec.signSha256Digest(MessageDigest.getInstance("SHA-256").digest(data));
        Signature verify = Signature.getInstance("SHA256withECDSA");
        verify.initVerify(h[0].getPublic());
        verify.update(data);
        assertThat(verify.verify(sig)).isTrue();
        assertThat(SignerConfig.parse(new String[]{"--port", "17300", "--origin", "https://a,https://b"}).allowedOrigins()).containsExactly("https://a", "https://b");
        assertThat(SignerConfig.parse(new String[0]).sunPkcs11Config()).contains("library = ").contains("slot = 0");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        keyStore("RSA", "x", new KeyPair[1]).store(bos, PIN);
        assertThat(TokenKeyStore.fromPkcs12(bos.toByteArray(), PIN).containsAlias("x")).isTrue();
    }
}
