package pl.ejtechnology.tk.eSkarga.signer;

import java.security.KeyStore;

/**
 * Uruchomienie na stacji sędziego: {@code java -jar eskarga-local-signer.jar [--lib ścieżka/libykcs11] [--slot 0] [--port 17227] [--origin https://eskarga.tk.internal]}.
 * PIN ze zmiennej {@code ESKARGA_SIGNER_PIN} albo z konsoli. Tryb {@code --pkcs12 plik.p12} tylko do testów.
 */
public final class LocalSignerMain {

    private LocalSignerMain() {
    }

    public static void main(String[] args) throws Exception {
        SignerConfig cfg = SignerConfig.parse(args);
        char[] pin = TokenKeyStore.readPin(cfg);
        KeyStore ks = TokenKeyStore.open(cfg, pin);
        DigestSigner signer = new DigestSigner(ks, pin, null);
        LocalSignerServer server = new LocalSignerServer(signer, cfg.allowedOrigins());
        int port = server.start(cfg.port());
        System.out.println("eSkarga – aplikacja do podpisu: http://127.0.0.1:" + port + " (klucz: " + signer.alias() + ", " + signer.signatureAlgorithm() + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
