package pl.ejtechnology.tk.eSkarga.docs.hsm;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Podpis łańcucha dziennika zdarzeń kluczem z HSM (T-037, NFR-02): aplikacja przesyła ostatni skrót dnia, usługa zwraca
 * podpis RSA-PSS/SHA-256 i certyfikat; weryfikator łańcucha sprawdza podpis przy audycie.
 */
public final class AuditChainSigner {

    public static final String ALGORITHM = "SHA256withRSA";

    /** Podpis i certyfikat (DER, base64). */
    public record Signed(String signatureBase64, String certificateBase64) {
    }

    private final KeyStore keyStore;
    private final String alias;
    private final char[] pin;

    public AuditChainSigner(KeyStore keyStore, String alias, char[] pin) {
        this.keyStore = keyStore;
        this.alias = alias;
        this.pin = pin;
    }

    public Signed sign(String chainHeadHash) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(alias, pin);
            if (key == null) {
                throw new IllegalStateException("Brak klucza '" + alias + "' w HSM");
            }
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(key);
            sig.update(chainHeadHash.getBytes(StandardCharsets.US_ASCII));
            Certificate cert = keyStore.getCertificate(alias);
            return new Signed(Base64.getEncoder().encodeToString(sig.sign()), Base64.getEncoder().encodeToString(cert.getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Podpis łańcucha audytu nie powiódł się", e);
        }
    }

    public static boolean verify(String chainHeadHash, Signed signed) {
        try {
            byte[] der = Base64.getDecoder().decode(signed.certificateBase64());
            X509Certificate cert = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(der));
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(cert.getPublicKey());
            sig.update(chainHeadHash.getBytes(StandardCharsets.US_ASCII));
            return sig.verify(Base64.getDecoder().decode(signed.signatureBase64()));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
