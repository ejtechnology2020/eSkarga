package pl.ejtechnology.tk.eSkarga.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** NFR-02 (T-015): modyfikacja wpisu w bazie jest wykrywana przez weryfikator łańcucha. */
class AuditChainTest {

    private static AuditEvent ev(String user, String action, String prev) {
        return AuditHash.chain(new AuditEvent(Instant.parse("2026-11-03T08:42:11Z"), user, "referent", action, "DW 2026/04812", "Ts 312/26", "10.12.2.5"), prev);
    }

    @Test
    void intactChainVerifies() {
        AuditEvent a = ev("kwzorek", "rejestracja wpływu", null);
        AuditEvent b = ev("mprzykladowski", "kwalifikacja", a.getHash());
        AuditEvent c = ev("tprobny", "wyświetlenie akt", b.getHash());
        AuditChainVerifier.Result r = AuditChainVerifier.verify(List.of(a, b, c));
        assertThat(r.consistent()).isTrue();
        assertThat(r.checked()).isEqualTo(3);
        assertThat(a.getPrevHash()).isEqualTo(AuditHash.GENESIS);
    }

    @Test
    void tamperedEntryIsDetected() {
        AuditEvent a = ev("kwzorek", "rejestracja wpływu", null);
        AuditEvent b = ev("mprzykladowski", "kwalifikacja", a.getHash());
        AuditEvent c = ev("tprobny", "wyświetlenie akt", b.getHash());
        // „modyfikacja w bazie”: ktoś zmienia treść b bez przeliczenia skrótu
        AuditEvent bTampered = new AuditEvent(b.getOccurredAt(), "aadmin", b.getUserRole(), b.getAction(), b.getObjectRef(), b.getCaseRef(), b.getRemoteAddr());
        bTampered.setPrevHash(b.getPrevHash());
        bTampered.setHash(b.getHash());
        AuditChainVerifier.Result r = AuditChainVerifier.verify(List.of(a, bTampered, c));
        assertThat(r.consistent()).isFalse();
        assertThat(r.checked()).isEqualTo(1);
        assertThat(r.firstBroken()).containsSame(bTampered);
    }

    @Test
    void removedEntryBreaksChain() {
        AuditEvent a = ev("kwzorek", "rejestracja wpływu", null);
        AuditEvent b = ev("mprzykladowski", "kwalifikacja", a.getHash());
        AuditEvent c = ev("tprobny", "wyświetlenie akt", b.getHash());
        assertThat(AuditChainVerifier.verify(List.of(a, c)).consistent()).isFalse();
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        assertThat(AuditHash.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
