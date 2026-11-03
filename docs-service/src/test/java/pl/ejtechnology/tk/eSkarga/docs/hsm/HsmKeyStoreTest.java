package pl.ejtechnology.tk.eSkarga.docs.hsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.ejtechnology.tk.eSkarga.docs.support.TestCerts;

/** T-037: magazyn kluczy (tryb pkcs12 tylko poza produkcją) i podpis łańcucha audytu. */
class HsmKeyStoreTest {

    @TempDir
    Path tmp;

    @Test
    void podpisLancuchaAudytuJestWeryfikowalny() throws Exception {
        TestCerts.Identity id = TestCerts.selfSigned("eSkarga audyt");
        Path file = tmp.resolve("hsm-test.p12");
        Files.write(file, TestCerts.pkcs12Bytes(TestCerts.pkcs12(id, "audyt")));
        KeyStore ks = HsmKeyStore.open(new HsmKeyStore.Config(HsmKeyStore.MODE_PKCS12, null, file, TestCerts.PIN));
        AuditChainSigner signer = new AuditChainSigner(ks, "audyt", TestCerts.PIN);
        String head = "a".repeat(64);
        AuditChainSigner.Signed s = signer.sign(head);
        assertThat(AuditChainSigner.verify(head, s)).isTrue();
        assertThat(AuditChainSigner.verify("b".repeat(64), s)).isFalse();
        assertThatThrownBy(() -> new AuditChainSigner(ks, "brak", TestCerts.PIN).sign(head)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trybPkcs12ZabronionyNaProdukcji() throws Exception {
        System.setProperty("eskarga.environment", "PROD");
        try {
            assertThatThrownBy(() -> HsmKeyStore.open(new HsmKeyStore.Config(HsmKeyStore.MODE_PKCS12, null, tmp.resolve("x.p12"), TestCerts.PIN)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("D12");
        } finally {
            System.clearProperty("eskarga.environment");
        }
        assertThat(HsmKeyStore.softHsmConfig("/usr/lib/softhsm/libsofthsm2.so", 0)).contains("library = /usr/lib/softhsm/libsofthsm2.so").doesNotContain("pin");
    }
}
