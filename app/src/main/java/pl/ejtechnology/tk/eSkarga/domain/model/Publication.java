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

/** Postanowienie w kolejce do publikacji po anonimizacji (FR-22, FR-29, UC-16). */
@Entity
@Table(name = "publication")
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CaseDocument document;

    @Column(name = "anonymised_key", length = 400)
    private String anonymisedKey;

    @Column(name = "replacements", nullable = false)
    private int replacements;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.OCZEKUJE;

    protected Publication() {
    }

    public Publication(CaseDocument document) {
        this.document = document;
    }

    public void anonymised(String key, int replacements) {
        this.anonymisedKey = key;
        this.replacements = replacements;
    }

    /** Zatwierdzenie anonimizacji przez człowieka; dokument z flagą art. 80 bez wersji zanonimizowanej nie może być opublikowany. */
    public void approve(String by, Instant at, boolean caseFlaggedArt80) {
        if (anonymisedKey == null && caseFlaggedArt80) {
            throw new IllegalStateException("Dokument z zastrzeżeniem art. 80 wymaga wersji zanonimizowanej przed publikacją (FR-22)");
        }
        this.approvedBy = by;
        this.approvedAt = at;
        this.status = PublicationStatus.ZATWIERDZONA;
    }

    public void requestFix() {
        this.status = PublicationStatus.POPRAWKA;
    }

    public void published(Instant at) {
        this.publishedAt = at;
        this.status = PublicationStatus.OPUBLIKOWANA;
    }

    public Long getId() { return id; }
    public CaseDocument getDocument() { return document; }
    public String getAnonymisedKey() { return anonymisedKey; }
    public int getReplacements() { return replacements; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public PublicationStatus getStatus() { return status; }
}
