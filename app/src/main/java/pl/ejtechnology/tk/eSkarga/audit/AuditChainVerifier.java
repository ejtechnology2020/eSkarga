package pl.ejtechnology.tk.eSkarga.audit;

import java.util.List;
import java.util.Optional;

/**
 * Weryfikator łańcucha (NFR-02, polecenie z T-015): przelicza skróty po kolei i zwraca pierwszy niespójny wpis.
 * Wynik trafia na ekran S-25 („Łańcuch spójny do …” albo alarm) i do SIEM.
 */
public final class AuditChainVerifier {

    /** Wynik weryfikacji: liczba sprawdzonych wpisów i ewentualny pierwszy błędny wpis. */
    public record Result(long checked, Optional<AuditEvent> firstBroken) {
        public boolean consistent() {
            return firstBroken.isEmpty();
        }
    }

    private AuditChainVerifier() {
    }

    public static Result verify(List<AuditEvent> eventsInOrder) {
        String prev = AuditHash.GENESIS;
        long checked = 0;
        for (AuditEvent e : eventsInOrder) {
            if (!prev.equals(e.getPrevHash()) || !AuditHash.sha256(e.canonical()).equals(e.getHash())) {
                return new Result(checked, Optional.of(e));
            }
            prev = e.getHash();
            checked++;
        }
        return new Result(checked, Optional.empty());
    }
}
