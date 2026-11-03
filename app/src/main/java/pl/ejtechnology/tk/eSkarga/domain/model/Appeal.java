package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;
import java.time.LocalDate;

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

/** Zażalenie na odmowę nadania dalszego biegu (FR-18, UC-11). Ocena terminowości jest pomocnicza; skład rozstrzyga. */
@Entity
@Table(name = "appeal")
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "in_time")
    private Boolean inTime;

    @Column(name = "panel_user_ids", length = 400)
    private String panelUserIds;

    @Column(name = "session_on")
    private LocalDate sessionOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40)
    private AppealOutcome outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Appeal() {
    }

    public Appeal(CaseFile caseFile, LocalDate filedOn, Boolean inTime) {
        this.caseFile = caseFile;
        this.filedOn = filedOn;
        this.inTime = inTime;
    }

    public void referToPanel(String panelUserIds, LocalDate sessionOn) {
        this.panelUserIds = panelUserIds;
        this.sessionOn = sessionOn;
    }

    public void decide(AppealOutcome outcome) {
        this.outcome = outcome;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public LocalDate getFiledOn() { return filedOn; }
    public Boolean getInTime() { return inTime; }
    public String getPanelUserIds() { return panelUserIds; }
    public LocalDate getSessionOn() { return sessionOn; }
    public AppealOutcome getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
