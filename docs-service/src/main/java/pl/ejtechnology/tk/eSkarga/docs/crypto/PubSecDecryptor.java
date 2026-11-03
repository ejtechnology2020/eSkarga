package pl.ejtechnology.tk.eSkarga.docs.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Security;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Odszyfrowanie wpływających PDF-ów zaszyfrowanych certyfikatem TK (Adobe.PubSec, T-037). Klucz prywatny pochodzi
 * z magazynu HSM ({@link pl.ejtechnology.tk.eSkarga.docs.hsm.HsmKeyStore}); wynik zapisywany bez szyfrowania do akt
 * (szyfrowanie w spoczynku zapewnia repozytorium S3 – SSE).
 */
public final class PubSecDecryptor {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final KeyStore keyStore;
    private final String alias;
    private final char[] pin;

    public PubSecDecryptor(KeyStore keyStore, String alias, char[] pin) {
        this.keyStore = keyStore;
        this.alias = alias;
        this.pin = pin;
    }

    public static boolean isEncrypted(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.isEncrypted();
        } catch (IOException e) {
            // PDFBox zgłasza InvalidPasswordException / brak klucza dla dokumentów zaszyfrowanych certyfikatem
            return true;
        }
    }

    public byte[] decrypt(byte[] pdf) {
        try (ByteArrayOutputStream ks = new ByteArrayOutputStream()) {
            keyStore.store(ks, pin);
            try (PDDocument doc = Loader.loadPDF(pdf, new String(pin), new ByteArrayInputStream(ks.toByteArray()), alias);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.setAllSecurityToBeRemoved(true);
                doc.save(out);
                return out.toByteArray();
            }
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Nie udało się odszyfrować dokumentu certyfikatem TK", e);
        }
    }
}
