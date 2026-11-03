package pl.ejtechnology.tk.eSkarga.docs.signature;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Kontrakt aplikacji lokalnej do podpisu kartą (D10, ADR 0008): przeglądarka przekazuje skrót, aplikacja lokalna zwraca
 * wartość podpisu RSA/ECDSA z karty. Serwer nigdy nie widzi klucza prywatnego. Implementacja produkcyjna – po decyzji
 * (SDK dostawcy kart czy własna); implementacja testowa – klucz z PKCS#12.
 */
public interface LocalSignerClient {

    /** Certyfikat podpisującego i łańcuch (do parametrów PAdES). */
    List<X509Certificate> certificateChain();

    /** Podpis skrótu ({@code digestAlgorithm} np. SHA-256) kluczem z karty; zwraca surową wartość podpisu. */
    byte[] signDigest(byte[] digest, String digestAlgorithm);

    /** Algorytm podpisu karty, np. {@code RSA_SHA256}. */
    String signatureAlgorithm();
}
