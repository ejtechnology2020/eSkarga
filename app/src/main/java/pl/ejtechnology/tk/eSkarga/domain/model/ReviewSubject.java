package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Przedmiot kontroli: akt, jednostka redakcyjna, wzorzec kontroli (analiza §8.1). */
@Entity
@Table(name = "review_subject")
public class ReviewSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "act", nullable = false, length = 400)
    private String act;

    @Column(name = "unit", length = 120)
    private String unit;

    @Column(name = "standard", length = 400)
    private String standard;

    protected ReviewSubject() {
    }

    public ReviewSubject(CaseFile caseFile, String act, String unit, String standard) {
        this.caseFile = caseFile;
        this.act = act;
        this.unit = unit;
        this.standard = standard;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getAct() { return act; }
    public String getUnit() { return unit; }
    public String getStandard() { return standard; }
}
