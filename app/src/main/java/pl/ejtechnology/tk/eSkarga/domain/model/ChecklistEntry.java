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

import pl.ejtechnology.tk.eSkarga.domain.ChecklistState;

/** Ocena wymogu formalnego (lista kontrolna, UC-06) – wiersz tylko dopisywany; obowiązuje najnowszy dla sprawy i kodu wymogu (V9). */
@Entity
@Table(name = "checklist_entry")
public class ChecklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id")
    private CaseFile caseFile;

    @Column(name = "requirement_code", nullable = false, length = 60)
    private String requirementCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ChecklistState state;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "page_ref", length = 40)
    private String pageRef;

    @Column(name = "decided_by", nullable = false, length = 120)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ChecklistEntry() {
    }

    public ChecklistEntry(CaseFile caseFile, String requirementCode, ChecklistState state, String note, String pageRef, String decidedBy, Instant decidedAt) {
        this.caseFile = caseFile;
        this.requirementCode = requirementCode;
        this.state = state;
        this.note = note;
        this.pageRef = pageRef;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getRequirementCode() { return requirementCode; }
    public ChecklistState getState() { return state; }
    public String getNote() { return note; }
    public String getPageRef() { return pageRef; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
