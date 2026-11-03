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

/** Czynność procesowa (zarządzenie, postanowienie, notatka, przekazanie) z autorem, składem, podstawą prawną i dokumentem. */
@Entity
@Table(name = "procedural_act")
public class ProceduralAct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40)
    private ProceduralActKind kind;

    @Column(name = "subtype", length = 60)
    private String subtype;

    @Column(name = "acted_on", nullable = false)
    private Instant actedOn;

    @Column(name = "author_user_id", nullable = false, length = 120)
    private String authorUserId;

    @Column(name = "panel_user_ids", length = 400)
    private String panelUserIds;

    @Column(name = "legal_basis", length = 200)
    private String legalBasis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private CaseDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ActStatus status;

    protected ProceduralAct() {
    }

    public ProceduralAct(CaseFile caseFile, ProceduralActKind kind, String subtype, Instant actedOn, String authorUserId, String legalBasis, ActStatus status) {
        this.caseFile = caseFile;
        this.kind = kind;
        this.subtype = subtype;
        this.actedOn = actedOn;
        this.authorUserId = authorUserId;
        this.legalBasis = legalBasis;
        this.status = status;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public ProceduralActKind getKind() { return kind; }
    public String getSubtype() { return subtype; }
    public Instant getActedOn() { return actedOn; }
    public String getAuthorUserId() { return authorUserId; }
    public String getPanelUserIds() { return panelUserIds; }
    public void setPanelUserIds(String panelUserIds) { this.panelUserIds = panelUserIds; }
    public String getLegalBasis() { return legalBasis; }
    public CaseDocument getDocument() { return document; }
    public void setDocument(CaseDocument document) { this.document = document; }
    public ActStatus getStatus() { return status; }
    public void setStatus(ActStatus status) { this.status = status; }
}
