package pl.ejtechnology.tk.eSkarga.documents;

import java.io.InputStream;
import java.util.Optional;

/**
 * Repozytorium dokumentów (D5, T-030): pliki w S3 z Object Lock, w bazie metadane i SHA-256.
 * Interfejs bez zależności od dostawcy; implementacja na AWS SDK v2.
 */
public interface DocumentStore {

    /** Zapisany dokument: klucz, rozmiar i skrót SHA-256 policzony przy zapisie. */
    record Stored(String key, long size, String sha256, String contentType) {
    }

    /** Fragment pliku do strumieniowania z HTTP Range (brief: 200 stron bez pobierania całości). */
    record Range(long start, long endInclusive) {
    }

    Stored put(String key, InputStream content, long size, String contentType, boolean lockCompliance);

    InputStream get(String key, Optional<Range> range);

    /** Weryfikacja skrótu przy odczycie; niezgodność = błąd i zdarzenie audytu (T-030). */
    boolean verifySha256(String key, String expectedSha256);
}
