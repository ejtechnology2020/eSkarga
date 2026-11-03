package pl.ejtechnology.tk.eSkarga.signer;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Podpis gotowego skrótu kluczem z magazynu (YubiKey przez PKCS#11 albo PKCS#12 w testach). RSA: PKCS#1 v1.5 z DigestInfo
 * (SHA-256), ECDSA: podpis skrótu w DER – dokładnie to, czego oczekuje DSS {@code signDocument} w kroku 2 (D10).
 * Klucz prywatny nigdy nie opuszcza tokenu; aplikacja widzi tylko skrót.
 */
public final class DigestSigner {

    private static final byte[] SHA256_DIGEST_INFO = {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};

    private final KeyStore keyStore;
    private final char[] pin;
    private final String alias;

    public DigestSigner(KeyStore keyStore, char[] pin, String preferredAlias) throws GeneralSecurityException {
        this.keyStore = keyStore;
        this.pin = pin;
        this.alias = preferredAlias != null ? preferredAlias : firstSigningAlias(keyStore);
    }

    static String firstSigningAlias(KeyStore ks) throws GeneralSecurityException {
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String a = e.nextElement();
            if (ks.isKeyEntry(a)) {
                Certificate c = ks.getCertificate(a);
                if (c instanceof X509Certificate x && (x.getKeyUsage() == null || x.getKeyUsage()[0] || x.getKeyUsage()[1])) {
                    return a;
                }
            }
        }
        throw new GeneralSecurityException("Na tokenie nie ma klucza do podpisu");
    }

    public List<X509Certificate> certificateChain() throws GeneralSecurityException {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        List<X509Certificate> out = new ArrayList<>();
        if (chain != null) {
            for (Certificate c : chain) {
                out.add((X509Certificate) c);
            }
        } else {
            out.add((X509Certificate) keyStore.getCertificate(alias));
        }
        return out;
    }

    /** {@code RSA_SHA256} albo {@code ECDSA_SHA256} – nazwa algorytmu DSS do kroku 2. */
    public String signatureAlgorithm() throws GeneralSecurityException {
        return keyStore.getKey(alias, pin) instanceof ECKey ? "ECDSA_SHA256" : "RSA_SHA256";
    }

    public byte[] signSha256Digest(byte[] digest) throws GeneralSecurityException {
        if (digest == null || digest.length != 32) {
            throw new GeneralSecurityException("Skrót SHA-256 musi mieć 32 bajty");
        }
        PrivateKey key = (PrivateKey) keyStore.getKey(alias, pin);
        if (key instanceof RSAKey) {
            byte[] digestInfo = new byte[SHA256_DIGEST_INFO.length + 32];
            System.arraycopy(SHA256_DIGEST_INFO, 0, digestInfo, 0, SHA256_DIGEST_INFO.length);
            System.arraycopy(digest, 0, digestInfo, SHA256_DIGEST_INFO.length, 32);
            Signature sig = Signature.getInstance("NONEwithRSA", keyStore.getProvider().getName().startsWith("SunPKCS11") ? keyStore.getProvider() : Signature.getInstance("NONEwithRSA").getProvider());
            sig.initSign(key);
            sig.update(digestInfo);
            return sig.sign();
        }
        Signature sig = Signature.getInstance("NONEwithECDSA", keyStore.getProvider().getName().startsWith("SunPKCS11") ? keyStore.getProvider() : Signature.getInstance("NONEwithECDSA").getProvider());
        sig.initSign(key);
        sig.update(digest);
        return sig.sign();
    }

    public String alias() {
        return alias;
    }
}
