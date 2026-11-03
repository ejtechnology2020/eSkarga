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

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;

/** Wersja wpisu repertorium: status, sędzia, flagi, przedmiot, zakończenie. Wersja {@code > 1} to korekta z uzasadnieniem i akceptacją. */
@Entity
@Table(name = "case_file_version")
public class CaseFileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CaseStatus status;

    @Column(name = "judge_user_id", length = 120)
    private String judgeUserId;

    @Column(name = "flag_urgent", nullable = false)
    private boolean flagUrgent;

    @Column(name = "flag_anonymise", nullable = false)
    private boolean flagAnonymise;

    @Column(name = "flag_suspended", nullable = false)
    private boolean flagSuspended;

    @Column(name = "review_subject", length = 1000)
    private String reviewSubject;

    @Column(name = "review_standard", length = 1000)
    private String reviewStandard;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "closing_mode", length = 60)
    private ClosingMode closingMode;

    @Column(name = "related_docket", length = 20)
    private String relatedDocket;

    @Column(name = "justification", length = 2000)
    private String justification;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    /** Stan korekty (UC-22); {@code null} dla wersji tworzonych przez system przy zmianie statusu. */
    @Enumerated(EnumType.STRING)
    @Column(name = "correction_status", length = 20)
    private CorrectionStatus correctionStatus;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CaseFileVersion() {
    }

    public CaseFileVersion(CaseFile caseFile, int version, CaseStatus status, String createdBy) {
        this.caseFile = caseFile;
        this.version = version;
        this.status = status;
        this.createdBy = createdBy;
    }

    /** Kopia do nowej wersji (zmiana statusu albo korekta); pola zmienia wywołujący przed zapisem. */
    public CaseFileVersion next(CaseStatus newStatus, String by) {
        CaseFileVersion v = new CaseFileVersion(caseFile, version + 1, newStatus, by);
        v.judgeUserId = judgeUserId;
        v.flagUrgent = flagUrgent;
        v.flagAnonymise = flagAnonymise;
        v.flagSuspended = flagSuspended;
        v.reviewSubject = reviewSubject;
        v.reviewStandard = reviewStandard;
        v.closedOn = closedOn;
        v.closingMode = closingMode;
        v.relatedDocket = relatedDocket;
        return v;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public int getVersion() { return version; }
    public CaseStatus getStatus() { return status; }
    public String getJudgeUserId() { return judgeUserId; }
    public void setJudgeUserId(String judgeUserId) { this.judgeUserId = judgeUserId; }
    public boolean isFlagUrgent() { return flagUrgent; }
    public void setFlagUrgent(boolean flagUrgent) { this.flagUrgent = flagUrgent; }
    public boolean isFlagAnonymise() { return flagAnonymise; }
    public void setFlagAnonymise(boolean flagAnonymise) { this.flagAnonymise = flagAnonymise; }
    public boolean isFlagSuspended() { return flagSuspended; }
    public void setFlagSuspended(boolean flagSuspended) { this.flagSuspended = flagSuspended; }
    public String getReviewSubject() { return reviewSubject; }
    public void setReviewSubject(String reviewSubject) { this.reviewSubject = reviewSubject; }
    public String getReviewStandard() { return reviewStandard; }
    public void setReviewStandard(String reviewStandard) { this.reviewStandard = reviewStandard; }
    public LocalDate getClosedOn() { return closedOn; }
    public void setClosedOn(LocalDate closedOn) { this.closedOn = closedOn; }
    public ClosingMode getClosingMode() { return closingMode; }
    public void setClosingMode(ClosingMode closingMode) { this.closingMode = closingMode; }
    public String getRelatedDocket() { return relatedDocket; }
    public void setRelatedDocket(String relatedDocket) { this.relatedDocket = relatedDocket; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public CorrectionStatus getCorrectionStatus() { return correctionStatus; }
    public void setCorrectionStatus(CorrectionStatus correctionStatus) { this.correctionStatus = correctionStatus; }

    /** Wersja obowiązująca: systemowa albo korekta zatwierdzona przez kierownika. */
    public boolean isEffective() {
        return correctionStatus == null || correctionStatus == CorrectionStatus.ZATWIERDZONA;
    }

    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
