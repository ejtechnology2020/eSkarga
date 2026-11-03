package pl.ejtechnology.tk.eSkarga.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Łańcuch skrótów SHA-256: skrót wpisu obejmuje skrót poprzedniego wpisu. */
public final class AuditHash {

    /** Skrót „zerowy” pierwszego wpisu w łańcuchu. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHash() {
    }

    public static String sha256(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Brak SHA-256 w JVM", e);
        }
    }

    /** Dolicza skrót do wpisu, łącząc go z poprzednim. */
    public static AuditEvent chain(AuditEvent event, String previousHash) {
        event.setPrevHash(previousHash == null ? GENESIS : previousHash);
        event.setHash(sha256(event.canonical()));
        return event;
    }
}
