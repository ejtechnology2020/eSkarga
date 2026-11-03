package pl.ejtechnology.tk.eSkarga.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;

/** Repozytorium dokumentów w pamięci do testów serwisów (bez S3). Pamięta, czy zapis żądał Object Lock. */
public class InMemoryDocumentStore implements DocumentStore {

    public record Entry(byte[] bytes, String contentType, boolean locked) {
    }

    private final Map<String, Entry> files = new LinkedHashMap<>();

    @Override
    public Stored put(String key, InputStream content, long size, String contentType, boolean lockCompliance) {
        try {
            byte[] bytes = content.readAllBytes();
            files.put(key, new Entry(bytes, contentType, lockCompliance));
            return new Stored(key, bytes.length, sha256(bytes), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream get(String key, Optional<Range> range) {
        Entry e = files.get(key);
        if (e == null) {
            throw new IllegalArgumentException("Brak pliku " + key);
        }
        if (range.isPresent()) {
            int from = (int) range.get().start();
            int to = (int) Math.min(e.bytes().length - 1, range.get().endInclusive());
            byte[] part = new byte[to - from + 1];
            System.arraycopy(e.bytes(), from, part, 0, part.length);
            return new ByteArrayInputStream(part);
        }
        return new ByteArrayInputStream(e.bytes());
    }

    @Override
    public boolean verifySha256(String key, String expectedSha256) {
        Entry e = files.get(key);
        return e != null && sha256(e.bytes()).equalsIgnoreCase(expectedSha256);
    }

    public Map<String, Entry> files() {
        return files;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
