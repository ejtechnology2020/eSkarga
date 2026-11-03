package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Kategoria archiwalna i okres retencji dla sposobu zakończenia sprawy – konfiguracja, nie kod (ADR 0012). */
@Entity
@Table(name = "archive_category")
public class ArchiveCategory {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "closing_mode", length = 60)
    private ClosingMode closingMode;

    @Column(name = "category", nullable = false, length = 10)
    private String category;

    @Column(name = "retention_years")
    private Integer retentionYears;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ArchiveCategory() {
    }

    public ArchiveCategory(ClosingMode closingMode, String category, Integer retentionYears) {
        this.closingMode = closingMode;
        this.category = category;
        this.retentionYears = retentionYears;
    }

    public void update(String category, Integer retentionYears, String by, Instant at) {
        this.category = category;
        this.retentionYears = retentionYears;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public ClosingMode getClosingMode() { return closingMode; }
    public String getCategory() { return category; }
    public Integer getRetentionYears() { return retentionYears; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
