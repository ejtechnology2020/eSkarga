package pl.ejtechnology.tk.eSkarga.docs.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.support.TestCerts;
import pl.ejtechnology.tk.eSkarga.docs.support.TestPdfs;

/** D10, T-040, T-031: podpis dwuetapowy (skrót → karta → złożenie) i walidacja DSS; kolejne podpisy składu. */
class PadesSigningTest {

    /** Implementacja testowa aplikacji lokalnej: „karta” to klucz z PKCS#12 w pamięci. */
    static final class Pkcs12LocalSigner implements LocalSignerClient {
        private final Pkcs12SignatureToken token;
        private final DSSPrivateKeyEntry key;

        Pkcs12LocalSigner(KeyStore ks) throws Exception {
            token = new Pkcs12SignatureToken(TestCerts.pkcs12Bytes(ks), new KeyStore.PasswordProtection(TestCerts.PIN));
            key = token.getKeys().get(0);
        }

        @Override
        public List<X509Certificate> certificateChain() {
            return List.of(key.getCertificate().getCertificate());
        }

        @Override
        public byte[] signDigest(byte[] digest, String digestAlgorithm) {
            return token.signDigest(new Digest(DigestAlgorithm.SHA256, digest), key).getValue();
        }

        @Override
        public String signatureAlgorithm() {
            return "RSA_SHA256";
        }
    }

    static TestCerts.Identity judge;
    static TestCerts.Identity judge2;
    static Pkcs12LocalSigner signer;
    static Pkcs12LocalSigner signer2;

    @BeforeAll
    static void setUp() throws Exception {
        judge = TestCerts.selfSigned("sedzia TK Piotr Fikcyjny");
        judge2 = TestCerts.selfSigned("sedzia TK Maria Przykladowa");
        signer = new Pkcs12LocalSigner(TestCerts.pkcs12(judge, "sedzia"));
        signer2 = new Pkcs12LocalSigner(TestCerts.pkcs12(judge2, "sedzia2"));
    }

    @Test
    void dwuetapowyPodpisPrzechodziWalidacjeDss() throws Exception {
        byte[] pdf = TestPdfs.withText("POSTANOWIENIE Ts 312/26");
        TwoStepPadesSigner two = new TwoStepPadesSigner(null);
        TwoStepPadesSigner.DataToSign d = two.dataToSign(pdf, signer.certificateChain(), Instant.now(), "RSA_SHA256", "Podpis sędziego", "Warszawa");
        assertThat(d.digest()).hasSize(32);
        assertThat(two.openSessions()).isEqualTo(1);
        byte[] signed = two.signDocument(d.sessionId(), signer.signDigest(d.digest(), d.digestAlgorithm()));
        assertThat(two.openSessions()).as("sesja jednorazowa").isZero();
        assertThat(signed.length).isGreaterThan(pdf.length);

        SignatureReport trusted = new SignatureValidationService(List.of(judge.certificate())).validate(signed);
        assertThat(trusted.anySignature()).isTrue();
        assertThat(trusted.allValid()).isTrue();
        assertThat(trusted.signatures()).singleElement().satisfies(s -> {
            assertThat(s.indication()).isEqualTo("TOTAL_PASSED");
            assertThat(s.format()).contains("PAdES");
            assertThat(s.signedBy()).contains("Fikcyjny");
        });
        assertThat(trusted.toJson().getBoolean("wszystkieWazne")).isTrue();

        SignatureReport untrusted = new SignatureValidationService(List.of()).validate(signed);
        assertThat(untrusted.anySignature()).isTrue();
        assertThat(untrusted.allValid()).as("bez kotwicy zaufania podpis nie jest potwierdzony – ostrzeżenie dla referenta").isFalse();
        assertThat(untrusted.warning()).isNotBlank();

        assertThatThrownBy(() -> two.signDocument(d.sessionId(), new byte[256])).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void drugiSedziaDoklaadaPodpisAZmianaTresciUniewazniaPodpis() throws Exception {
        byte[] pdf = TestPdfs.withText("POSTANOWIENIE skladu Ts 312/26");
        TwoStepPadesSigner two = new TwoStepPadesSigner(null);
        byte[] once = two.sign(pdf, signer, Instant.now(), "Podpis 1", "Warszawa");
        byte[] twice = two.sign(once, signer2, Instant.now(), "Podpis 2", "Warszawa");
        SignatureReport r = new SignatureValidationService(List.of(judge.certificate(), judge2.certificate())).validate(twice);
        assertThat(r.signatures()).hasSize(2);
        assertThat(r.allValid()).isTrue();

        // zmiana bajtu w strumieniu treści pierwszej rewizji (objętej obydwoma podpisami) musi unieważnić podpisy
        byte[] tampered = twice.clone();
        int idx = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("stream") + 20;
        assertThat(idx).isGreaterThan(20);
        tampered[idx] = (byte) (tampered[idx] ^ 0x55);
        SignatureReport broken = new SignatureValidationService(List.of(judge.certificate(), judge2.certificate())).validate(tampered);
        assertThat(broken.allValid()).isFalse();
    }

    @Test
    void dokumentBezPodpisuMaPustyRaport() throws Exception {
        SignatureReport r = new SignatureValidationService(List.of()).validate(TestPdfs.withText("bez podpisu"));
        assertThat(r.anySignature()).isFalse();
        assertThat(r.warning()).isNull();
        assertThat(r.toJson().getBoolean("podpisany")).isFalse();
    }
}
