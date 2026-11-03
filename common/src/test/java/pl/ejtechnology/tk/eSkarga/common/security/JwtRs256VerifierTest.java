package pl.ejtechnology.tk.eSkarga.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Weryfikacja tokenów workerów (T-004, T-042): podpis, wydawca, odbiorca, ważność, role z konfigurowalnego claimu. */
class JwtRs256VerifierTest {

    private static KeyPair good;
    private static KeyPair other;
    private static JwtRs256Verifier verifier;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        good = g.generateKeyPair();
        other = g.generateKeyPair();
        verifier = new JwtRs256Verifier("https://id.example.test/realms/tk", "eskarga-api", "realm_access.roles",
                () -> Map.of("k1", (RSAPublicKey) good.getPublic()));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(KeyPair kp, String kid, String payload) throws Exception {
        String h = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}");
        String p = b64(payload);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update((h + "." + p).getBytes(StandardCharsets.US_ASCII));
        return h + "." + p + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
    }

    private static String payload(String iss, String aud, long exp, String roles) {
        return "{\"iss\":\"" + iss + "\",\"aud\":" + aud + ",\"exp\":" + exp + ",\"sub\":\"worker-doreczenia\",\"realm_access\":{\"roles\":[" + roles + "]}}";
    }

    @Test
    void poprawnyTokenDajeRoszczenia() throws Exception {
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        var claims = verifier.verify(token(good, "k1", payload("https://id.example.test/realms/tk", "\"eskarga-api\"", exp, "\"eskarga-worker\",\"inna\"")));
        assertThat(claims).isPresent();
        assertThat(claims.get().subject()).isEqualTo("worker-doreczenia");
        assertThat(claims.get().roles()).containsExactlyInAnyOrder("eskarga-worker", "inna");
        assertThat(verifier.verify(token(good, "k1", payload("https://id.example.test/realms/tk", "[\"x\",\"eskarga-api\"]", exp, "\"eskarga-worker\"")))).isPresent();
    }

    @Test
    void azpZastepujeBrakujaceAud() throws Exception {
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        String payload = "{\"iss\":\"https://id.example.test/realms/tk\",\"azp\":\"eskarga-api\",\"exp\":" + exp + ",\"sub\":\"w\",\"realm_access\":{\"roles\":[\"eskarga-worker\"]}}";
        assertThat(verifier.verify(token(good, "k1", payload))).isPresent();
        String other = payload.replace("\"azp\":\"eskarga-api\"", "\"azp\":\"inny\"");
        assertThat(verifier.verify(token(good, "k1", other))).isEmpty();
    }

    @Test
    void odrzucaObcyKluczWydawceOdbiorceIPrzeterminowany() throws Exception {
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        assertThat(verifier.verify(token(other, "k1", payload("https://id.example.test/realms/tk", "\"eskarga-api\"", exp, "\"eskarga-worker\"")))).as("podpis innym kluczem").isEmpty();
        assertThat(verifier.verify(token(good, "k1", payload("https://zly.example.test", "\"eskarga-api\"", exp, "\"eskarga-worker\"")))).as("inny wydawca").isEmpty();
        assertThat(verifier.verify(token(good, "k1", payload("https://id.example.test/realms/tk", "\"inna-aplikacja\"", exp, "\"eskarga-worker\"")))).as("inny odbiorca").isEmpty();
        long past = Instant.now().minusSeconds(3600).getEpochSecond();
        assertThat(verifier.verify(token(good, "k1", payload("https://id.example.test/realms/tk", "\"eskarga-api\"", past, "\"eskarga-worker\"")))).as("przeterminowany").isEmpty();
        assertThat(verifier.verify("nie.jwt")).isEmpty();
        String none = b64("{\"alg\":\"none\"}") + "." + b64(payload("https://id.example.test/realms/tk", "\"eskarga-api\"", exp, "\"eskarga-worker\"")) + ".";
        assertThat(verifier.verify(none)).as("alg=none nigdy nie przechodzi").isEmpty();
    }

    @Test
    void parsujeJwks() {
        RSAPublicKey pub = (RSAPublicKey) good.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(pub.getModulus().toByteArray()));
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());
        Map<String, RSAPublicKey> keys = JwtRs256Verifier.parseJwks("{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"use\":\"sig\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"},"
                + "{\"kty\":\"EC\",\"kid\":\"ec\"},{\"kty\":\"RSA\",\"kid\":\"enc\",\"use\":\"enc\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}");
        assertThat(keys).containsOnlyKeys("k1");
        assertThat(keys.get("k1").getModulus()).isEqualTo(pub.getModulus());
    }

    private static byte[] unsigned(byte[] b) {
        if (b.length > 1 && b[0] == 0) {
            byte[] out = new byte[b.length - 1];
            System.arraycopy(b, 1, out, 0, out.length);
            return out;
        }
        return b;
    }
}
