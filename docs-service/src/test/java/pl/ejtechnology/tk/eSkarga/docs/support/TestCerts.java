package pl.ejtechnology.tk.eSkarga.docs.support;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Certyfikaty testowe (fikcyjne, samopodpisane) do podpisu PAdES, PubSec i HSM w trybie PKCS#12. */
public final class TestCerts {

    public static final char[] PIN = "test-pin".toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public record Identity(KeyPair keyPair, X509Certificate certificate) {
    }

    private TestCerts() {
    }

    public static Identity selfSigned(String cn) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        X500Name name = new X500Name("CN=" + cn + ", O=Trybunał Konstytucyjny (test), C=PL");
        Instant now = Instant.now();
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.toEpochMilli()), Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)), name, kp.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));
        return new Identity(kp, cert);
    }

    public static KeyStore pkcs12(Identity id, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, PIN);
        ks.setKeyEntry(alias, id.keyPair().getPrivate(), PIN, new java.security.cert.Certificate[]{id.certificate()});
        return ks;
    }

    public static byte[] pkcs12Bytes(KeyStore ks) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ks.store(out, PIN);
            return out.toByteArray();
        }
    }
}
