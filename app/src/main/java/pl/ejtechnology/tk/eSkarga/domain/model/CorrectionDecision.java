package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Decyzja kierownika o korekcie wpisu (UC-22) – dopisywana, nigdy nie zmienia wersji wpisu (NFR-01). */
@Entity
@Table(name = "correction_decision")
public class CorrectionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private CaseFileVersion version;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private CorrectionStatus decision;

    @Column(name = "decided_by", nullable = false, length = 120)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    protected CorrectionDecision() {
    }

    public CorrectionDecision(CaseFileVersion version, CorrectionStatus decision, String decidedBy, Instant decidedAt, String remarks) {
        if (decision == CorrectionStatus.OCZEKUJE) {
            throw new IllegalArgumentException("Decyzja musi być zatwierdzeniem albo odrzuceniem");
        }
        this.version = version;
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public CaseFileVersion getVersion() { return version; }
    public CorrectionStatus getDecision() { return decision; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getRemarks() { return remarks; }
}
