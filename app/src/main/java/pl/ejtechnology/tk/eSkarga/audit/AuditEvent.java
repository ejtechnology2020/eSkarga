package pl.ejtechnology.tk.eSkarga.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Wpis dziennika zdarzeń (NFR-02): kto, rola, co, obiekt, sprawa, czas, adres, skrót poprzedniego wpisu i własny skrót
 * (SHA-256). Tabela tylko do dopisywania; weryfikator łańcucha wykrywa każdą modyfikację.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "user_role", length = 60)
    private String userRole;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "object_ref", length = 200)
    private String objectRef;

    @Column(name = "case_ref", length = 20)
    private String caseRef;

    @Column(name = "remote_addr", length = 45)
    private String remoteAddr;

    @JdbcTypeCode(SqlTypes.CHAR)   // PostgreSQL zgłasza bpchar jako CHAR – bez tego hbm2ddl=validate oczekuje VARCHAR
    @Column(name = "prev_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String prevHash;

    @JdbcTypeCode(SqlTypes.CHAR)   // PostgreSQL zgłasza bpchar jako CHAR – bez tego hbm2ddl=validate oczekuje VARCHAR
    @Column(name = "hash", nullable = false, columnDefinition = "CHAR(64)")
    private String hash;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, String userId, String userRole, String action, String objectRef, String caseRef, String remoteAddr) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.userRole = userRole;
        this.action = action;
        this.objectRef = objectRef;
        this.caseRef = caseRef;
        this.remoteAddr = remoteAddr;
    }

    /** Dane wpisu w ustalonej kolejności – wejście funkcji skrótu. */
    public String canonical() {
        return String.join("|", String.valueOf(occurredAt), userId, nz(userRole), action, nz(objectRef), nz(caseRef), nz(remoteAddr), prevHash);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getUserId() { return userId; }
    public String getUserRole() { return userRole; }
    public String getAction() { return action; }
    public String getObjectRef() { return objectRef; }
    public String getCaseRef() { return caseRef; }
    public String getRemoteAddr() { return remoteAddr; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
