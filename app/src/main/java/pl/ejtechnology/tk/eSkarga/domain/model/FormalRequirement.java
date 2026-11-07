package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.RequirementCondition;

/**
 * Wymóg formalny listy kontrolnej (UC-06, FR-11, S-24): osobno dla skarg (Ts) i wniosków (Tw). Kod jest stały (oceny w
 * {@code checklist_entry} odwołują się do niego), treść i podstawę administrator zmienia, pozycję wycofuje zamiast usuwać (ADR 0015).
 */
@Entity
@Table(name = "formal_requirement", uniqueConstraints = @UniqueConstraint(name = "uq_formal_requirement", columnNames = {"symbol", "code"}))
public class FormalRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol", nullable = false, length = 4)
    private DocketSymbol symbol;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", nullable = false, length = 600)
    private String description;

    @Column(name = "legal_basis", nullable = false, length = 160)
    private String legalBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_code", nullable = false, length = 30)
    private RequirementCondition condition = RequirementCondition.ZAWSZE;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FormalRequirement() {
    }

    public FormalRequirement(DocketSymbol symbol, String code, String description, String legalBasis, RequirementCondition condition, int position, String createdBy, Instant now) {
        this.symbol = symbol;
        this.code = code;
        this.description = description;
        this.legalBasis = legalBasis;
        this.condition = condition;
        this.position = position;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void update(String description, String legalBasis, RequirementCondition condition, int position, String by, Instant now) {
        this.description = description;
        this.legalBasis = legalBasis;
        this.condition = condition;
        this.position = position;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public void setActive(boolean active, String by, Instant now) {
        this.active = active;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public DocketSymbol getSymbol() { return symbol; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getLegalBasis() { return legalBasis; }
    public RequirementCondition getCondition() { return condition; }
    public int getPosition() { return position; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
