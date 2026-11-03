package pl.ejtechnology.tk.eSkarga.domain.model;

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

/** Przydział użytkownika do sprawy (D4): sędzia, zespół sędziego, referent. Zwolnienie zapisuje datę, nie usuwa. */
@Entity
@Table(name = "case_assignment")
public class CaseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AssignmentRole role;

    @Column(name = "assigned_on", nullable = false)
    private LocalDate assignedOn;

    @Column(name = "released_on")
    private LocalDate releasedOn;

    protected CaseAssignment() {
    }

    public CaseAssignment(CaseFile caseFile, String userId, AssignmentRole role, LocalDate assignedOn) {
        this.caseFile = caseFile;
        this.userId = userId;
        this.role = role;
        this.assignedOn = assignedOn;
    }

    public void release(LocalDate on) {
        this.releasedOn = on;
    }

    public boolean isActive() {
        return releasedOn == null;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getUserId() { return userId; }
    public AssignmentRole getRole() { return role; }
    public LocalDate getAssignedOn() { return assignedOn; }
    public LocalDate getReleasedOn() { return releasedOn; }
}
