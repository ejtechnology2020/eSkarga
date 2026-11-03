package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Ostateczne orzeczenie – początek terminu z art. 77; nadzwyczajny środek zaskarżenia zawiesza (art. 78). */
@Entity
@Table(name = "final_ruling")
public class FinalRuling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "authority", nullable = false, length = 400)
    private String authority;

    @Column(name = "ruling_ref", length = 120)
    private String rulingRef;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "delivered_on")
    private LocalDate deliveredOn;

    @Column(name = "extraordinary_remedy", nullable = false)
    private boolean extraordinaryRemedy;

    protected FinalRuling() {
    }

    public FinalRuling(CaseFile caseFile, String authority, String rulingRef, LocalDate issuedOn, LocalDate deliveredOn, boolean extraordinaryRemedy) {
        this.caseFile = caseFile;
        this.authority = authority;
        this.rulingRef = rulingRef;
        this.issuedOn = issuedOn;
        this.deliveredOn = deliveredOn;
        this.extraordinaryRemedy = extraordinaryRemedy;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getAuthority() { return authority; }
    public String getRulingRef() { return rulingRef; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public LocalDate getDeliveredOn() { return deliveredOn; }
    public boolean isExtraordinaryRemedy() { return extraordinaryRemedy; }
}
