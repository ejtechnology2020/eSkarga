package pl.ejtechnology.tk.eSkarga.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Wniosek o wgląd w akta (FR-27, UC-15). Osoba trzecia dostaje wyłącznie wersję zanonimizowaną (BR-09). */
@Entity
@Table(name = "access_request")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "requester", nullable = false, length = 400)
    private String requester;

    @Column(name = "participant", nullable = false)
    private boolean participant;

    @Column(name = "scope", length = 600)
    private String scope;

    @Column(name = "fee_pln", precision = 10, scale = 2)
    private BigDecimal feePln;

    @Column(name = "reading_room_at")
    private Instant readingRoomAt;

    @Column(name = "anonymised_ready", nullable = false)
    private boolean anonymisedReady;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AccessRequest() {
    }

    public AccessRequest(CaseFile caseFile, String requester, boolean participant, String scope, BigDecimal feePln) {
        this.caseFile = caseFile;
        this.requester = requester;
        this.participant = participant;
        this.scope = scope;
        this.feePln = feePln;
    }

    /** Termin w czytelni: dla osoby trzeciej dopiero po przygotowaniu wersji zanonimizowanej (S-13). */
    public void scheduleReadingRoom(Instant at) {
        if (!participant && !anonymisedReady) {
            throw new IllegalStateException("Dla osoby trzeciej wersja zanonimizowana musi mieć status „gotowa” (BR-09)");
        }
        this.readingRoomAt = at;
    }

    public void anonymisedReady() {
        this.anonymisedReady = true;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getRequester() { return requester; }
    public boolean isParticipant() { return participant; }
    public String getScope() { return scope; }
    public BigDecimal getFeePln() { return feePln; }
    public Instant getReadingRoomAt() { return readingRoomAt; }
    public boolean isAnonymisedReady() { return anonymisedReady; }
    public Instant getCreatedAt() { return createdAt; }
}
