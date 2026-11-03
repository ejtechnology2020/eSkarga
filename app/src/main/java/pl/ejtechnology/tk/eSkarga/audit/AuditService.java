package pl.ejtechnology.tk.eSkarga.audit;

import java.time.Instant;
import java.util.List;

import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Dziennik zdarzeń (T-015). Każda czynność i każdy odczyt dokumentu trafia do {@code audit_event}; wpis jest
 * dowiązany do poprzedniego skrótem. Singleton z blokadą zapisu gwarantuje ciągłość łańcucha w jednym węźle;
 * w klastrze ostatni skrót jest czytany z bazy pod blokadą wiersza.
 */
@Singleton
public class AuditService {

    @PersistenceContext(unitName = "repertorium")
    private EntityManager em;

    @Lock(LockType.WRITE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public AuditEvent record(String userId, String userRole, String action, String objectRef, String caseRef, String remoteAddr) {
        String prev = lastHash();
        AuditEvent event = AuditHash.chain(new AuditEvent(Instant.now(), userId, userRole, cut(action, 80), cut(objectRef, 200), caseRef, remoteAddr), prev);
        em.persist(event);
        // wysyłka do SIEM (syslog JSON) – adapter w T-015, poza tą transakcją (outbox)
        return event;
    }

    /**
     * Odmowa dostępu lub czynności (BR-10, D4) musi zostać w dzienniku także wtedy, gdy transakcja biznesowa
     * zostaje wycofana wyjątkiem – dlatego własna transakcja ({@code REQUIRES_NEW}).
     */
    @Lock(LockType.WRITE)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public AuditEvent recordDenial(String userId, String userRole, String action, String objectRef, String caseRef, String remoteAddr) {
        return record(userId, userRole, action, objectRef, caseRef, remoteAddr);
    }

    @Lock(LockType.READ)
    public AuditChainVerifier.Result verifyChain() {
        List<AuditEvent> all = em.createQuery("select e from AuditEvent e order by e.id", AuditEvent.class).getResultList();
        return AuditChainVerifier.verify(all);
    }

    /** Kolumny mają stałą długość; dłuższy opis jest skracany zamiast przerywać czynność. */
    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String lastHash() {
        List<String> last = em.createQuery("select e.hash from AuditEvent e order by e.id desc", String.class).setMaxResults(1).getResultList();
        return last.isEmpty() ? AuditHash.GENESIS : last.get(0);
    }
}
