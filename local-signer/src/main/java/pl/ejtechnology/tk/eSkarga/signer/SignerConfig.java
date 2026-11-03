package pl.ejtechnology.tk.eSkarga.signer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Konfiguracja aplikacji lokalnej (ADR 0008, decyzja 03.11.2026: certyfikat niekwalifikowany na kluczu YubiKey).
 * Domyślnie biblioteka PKCS#11 YubiKey ({@code libykcs11}); tryb {@code --pkcs12} wyłącznie do testów.
 */
public record SignerConfig(Path pkcs11Library, long slot, Path pkcs12, int port, List<String> allowedOrigins, String pinEnv) {

    public static final int DEFAULT_PORT = 17227;

    public static SignerConfig parse(String[] args) {
        Path lib = Path.of(defaultLibrary());
        long slot = 0;
        Path pkcs12 = null;
        int port = DEFAULT_PORT;
        List<String> origins = List.of("https://eskarga.tk.internal", "http://localhost:8080");
        String pinEnv = "ESKARGA_SIGNER_PIN";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--lib" -> lib = Path.of(args[++i]);
                case "--slot" -> slot = Long.parseLong(args[++i]);
                case "--pkcs12" -> pkcs12 = Path.of(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--origin" -> origins = Arrays.asList(args[++i].split(","));
                case "--pin-env" -> pinEnv = args[++i];
                default -> throw new IllegalArgumentException("Nieznany argument: " + args[i]);
            }
        }
        return new SignerConfig(lib, slot, pkcs12, port, origins, pinEnv);
    }

    static String defaultLibrary() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "C:\\Program Files\\Yubico\\Yubico PIV Tool\\bin\\libykcs11.dll";
        }
        if (os.contains("mac")) {
            return "/usr/local/lib/libykcs11.dylib";
        }
        return "/usr/lib/x86_64-linux-gnu/libykcs11.so";
    }

    /** Treść konfiguracji SunPKCS11 dla YubiKey (slot PIV 9c „Digital Signature” – klucz z wymaganym PIN przy każdym użyciu). */
    public String sunPkcs11Config() {
        return "name = YubiKeyPIV\nlibrary = " + pkcs11Library + "\nslot = " + slot + "\n";
    }
}
