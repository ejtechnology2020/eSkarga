package pl.ejtechnology.tk.eSkarga.signer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

/** Magazyn kluczy: YubiKey przez SunPKCS11 (ykcs11) albo PKCS#12 (tylko testy). */
public final class TokenKeyStore {

    private TokenKeyStore() {
    }

    public static KeyStore open(SignerConfig cfg, char[] pin) throws GeneralSecurityException, IOException {
        if (cfg.pkcs12() != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(cfg.pkcs12())) {
                ks.load(in, pin);
            }
            return ks;
        }
        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new GeneralSecurityException("Brak dostawcy SunPKCS11");
        }
        Provider configured = base.configure("--" + cfg.sunPkcs11Config());
        if (Security.getProvider(configured.getName()) == null) {
            Security.addProvider(configured);
        }
        KeyStore ks = KeyStore.getInstance("PKCS11", configured);
        ks.load(null, pin);
        return ks;
    }

    /** Pomocniczo: magazyn PKCS#12 z bajtów (testy). */
    public static KeyStore fromPkcs12(byte[] bytes, char[] pin) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), pin);
        return ks;
    }

    static char[] readPin(SignerConfig cfg) {
        String env = System.getenv(cfg.pinEnv());
        if (env != null && !env.isBlank()) {
            return env.toCharArray();
        }
        if (System.console() != null) {
            return System.console().readPassword("PIN klucza YubiKey: ");
        }
        throw new IllegalStateException("Podaj PIN w zmiennej " + cfg.pinEnv() + " albo uruchom w terminalu");
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
