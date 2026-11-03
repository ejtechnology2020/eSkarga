package pl.ejtechnology.tk.eSkarga.docs.hsm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/**
 * Klucze wyłącznie w HSM (D12, T-037): magazyn kluczy otwierany przez SunPKCS11 (prod: HSM sieciowy; dev: SoftHSM2).
 * Tryb {@code pkcs12} istnieje tylko dla testów jednostkowych bez biblioteki PKCS#11 i jest odrzucany, gdy
 * {@code eskarga.environment=PROD}. PIN pochodzi ze zmiennej środowiskowej, nigdy z pliku w repozytorium.
 */
public final class HsmKeyStore {

    public static final String MODE_PKCS11 = "pkcs11";
    public static final String MODE_PKCS12 = "pkcs12";

    /** Konfiguracja magazynu. */
    public record Config(String mode, Path pkcs11Config, Path pkcs12File, char[] pin) {
        public static Config fromSettings() {
            String mode = Settings.value("eskarga.hsm.mode", MODE_PKCS11);
            String pin = Settings.value("eskarga.hsm.pin", "");
            return new Config(mode, Path.of(Settings.value("eskarga.hsm.pkcs11.config", "/etc/eskarga/pkcs11.cfg")),
                    Path.of(Settings.value("eskarga.hsm.pkcs12.path", "")), pin.toCharArray());
        }
    }

    private HsmKeyStore() {
    }

    public static KeyStore open(Config cfg) {
        try {
            if (MODE_PKCS12.equalsIgnoreCase(cfg.mode())) {
                if ("PROD".equalsIgnoreCase(Settings.value("eskarga.environment", "DEV"))) {
                    throw new IllegalStateException("Tryb pkcs12 jest zabroniony w środowisku produkcyjnym (D12)");
                }
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (InputStream in = Files.newInputStream(cfg.pkcs12File())) {
                    ks.load(in, cfg.pin());
                }
                return ks;
            }
            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new IllegalStateException("Brak dostawcy SunPKCS11 w JDK");
            }
            Provider configured = base.configure(cfg.pkcs11Config().toAbsolutePath().toString());
            if (Security.getProvider(configured.getName()) == null) {
                Security.addProvider(configured);
            }
            KeyStore ks = KeyStore.getInstance("PKCS11", configured);
            ks.load(null, cfg.pin());
            return ks;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Nie udało się otworzyć magazynu kluczy HSM", e);
        }
    }

    /** Treść pliku konfiguracyjnego SunPKCS11 dla SoftHSM2 (dev) – generowana przez Ansible/compose, nie trzymana z PIN-em. */
    public static String softHsmConfig(String libraryPath, long slot) {
        return "name = eSkargaSoftHSM\nlibrary = " + libraryPath + "\nslot = " + slot + "\n";
    }
}
