package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;

/** Upoważnienie (FR-36, BR-10): osoba, funkcja, od–do, akt podstawowy; odwołanie zapisuje datę, nie usuwa wpisu. */
@Entity
@Table(name = "delegation")
public class Delegation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "function_code", nullable = false, length = 60)
    private String functionCode;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "basis_doc_ref", length = 200)
    private String basisDocRef;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Delegation() {
    }

    public Delegation(String userId, String functionCode, LocalDate validFrom, LocalDate validTo, String basisDocRef, String createdBy) {
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("Koniec upoważnienia przed początkiem");
        }
        this.userId = userId;
        this.functionCode = functionCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.basisDocRef = basisDocRef;
        this.createdBy = createdBy;
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    public boolean validOn(LocalDate day) {
        return revokedAt == null && !day.isBefore(validFrom) && !day.isAfter(validTo);
    }

    public CaseAccessPolicy.Delegation toPolicy() {
        return new CaseAccessPolicy.Delegation(userId, functionCode, validFrom, validTo, revokedAt != null);
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getFunctionCode() { return functionCode; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public String getBasisDocRef() { return basisDocRef; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
