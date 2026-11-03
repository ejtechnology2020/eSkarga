package pl.ejtechnology.tk.eSkarga.docs.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.PublicKeyProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.PublicKeyRecipient;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.support.TestCerts;
import pl.ejtechnology.tk.eSkarga.docs.support.TestPdfs;

/** T-037: PDF zaszyfrowany certyfikatem TK (Adobe.PubSec) jest odszyfrowywany kluczem z magazynu. */
class PubSecDecryptorTest {

    @Test
    void odszyfrowujeDokumentZaszyfrowanyCertyfikatem() throws Exception {
        TestCerts.Identity tk = TestCerts.selfSigned("Trybunal Konstytucyjny - wplyw");
        byte[] plain = TestPdfs.withText("Skarga konstytucyjna - tresc poufna");
        byte[] encrypted;
        try (PDDocument doc = Loader.loadPDF(plain); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PublicKeyProtectionPolicy policy = new PublicKeyProtectionPolicy();
            PublicKeyRecipient recipient = new PublicKeyRecipient();
            recipient.setX509(tk.certificate());
            recipient.setPermission(new AccessPermission());
            policy.addRecipient(recipient);
            policy.setEncryptionKeyLength(256);
            doc.protect(policy);
            doc.save(out);
            encrypted = out.toByteArray();
        }
        assertThat(PubSecDecryptor.isEncrypted(encrypted)).isTrue();
        assertThat(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("tresc poufna");

        KeyStore ks = TestCerts.pkcs12(tk, "pubsec");
        byte[] decrypted = new PubSecDecryptor(ks, "pubsec", TestCerts.PIN).decrypt(encrypted);
        assertThat(PubSecDecryptor.isEncrypted(decrypted)).isFalse();
        assertThat(TestPdfs.text(decrypted)).contains("tresc poufna");
    }
}
