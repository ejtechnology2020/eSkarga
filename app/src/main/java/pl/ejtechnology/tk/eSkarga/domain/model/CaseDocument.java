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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;

/**
 * Dokument w aktach (FR-23): tom, karty, rodzaj, klucz w repozytorium S3 i SHA-256 (D5). Dokument niejawny ma tylko metadane
 * ({@code storageKey == null}, NFR-06). Notatka wewnętrzna ({@code internalNote}) nigdy nie trafia do akt jawnych (FR-24).
 */
@Entity
@Table(name = "case_document")
public class CaseDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "volume_no", nullable = false)
    private int volumeNo = 1;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(name = "title", nullable = false, length = 400)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private DocumentKind kind;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "storage_key", length = 400)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)   // PostgreSQL zgłasza bpchar jako CHAR – bez tego hbm2ddl=validate oczekuje VARCHAR
    @Column(name = "sha256", columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "classified", nullable = false)
    private boolean classified;

    @Column(name = "internal_note", nullable = false)
    private boolean internalNote;

    @Column(name = "anonymised_key", length = 400)
    private String anonymisedKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", length = 30)
    private SignatureStatus signatureStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id")
    private LetterTemplate templateVersion;

    @Column(name = "pdfa_compliant", nullable = false)
    private boolean pdfaCompliant;

    @Column(name = "ocr_done", nullable = false)
    private boolean ocrDone;

    @Column(name = "signature_report", length = 2000)
    private String signatureReport;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CaseDocument() {
    }

    public CaseDocument(CaseFile caseFile, String title, DocumentKind kind, LocalDate documentDate, String createdBy) {
        this.caseFile = caseFile;
        this.title = title;
        this.kind = kind;
        this.documentDate = documentDate;
        this.createdBy = createdBy;
        this.classified = kind == DocumentKind.NIEJAWNY;
        this.internalNote = kind == DocumentKind.NOTATKA;
    }

    /** Numer kart po dołączeniu do akt (paginacja ciągła w tomie). */
    public void paginate(int volume, int from, int to) {
        this.volumeNo = volume;
        this.pageFrom = from;
        this.pageTo = to;
    }

    public void attachFile(String storageKey, String sha256) {
        if (classified) {
            throw new IllegalStateException("Dokument niejawny nie może mieć pliku w systemie (NFR-06)");
        }
        this.storageKey = storageKey;
        this.sha256 = sha256;
    }

    public String pagesLabel() {
        if (pageFrom == null) {
            return "—";
        }
        return pageTo == null || pageTo.equals(pageFrom) ? "k. " + pageFrom : "k. " + pageFrom + "–" + pageTo;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public int getVolumeNo() { return volumeNo; }
    public Integer getPageFrom() { return pageFrom; }
    public Integer getPageTo() { return pageTo; }
    public String getTitle() { return title; }
    public DocumentKind getKind() { return kind; }
    public LocalDate getDocumentDate() { return documentDate; }
    public String getStorageKey() { return storageKey; }
    public String getSha256() { return sha256; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isClassified() { return classified; }
    public boolean isInternalNote() { return internalNote; }
    public String getAnonymisedKey() { return anonymisedKey; }
    public void setAnonymisedKey(String anonymisedKey) { this.anonymisedKey = anonymisedKey; }
    public SignatureStatus getSignatureStatus() { return signatureStatus; }
    public void setSignatureStatus(SignatureStatus signatureStatus) { this.signatureStatus = signatureStatus; }
    public LetterTemplate getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(LetterTemplate templateVersion) { this.templateVersion = templateVersion; }
    public boolean isPdfaCompliant() { return pdfaCompliant; }
    public void setPdfaCompliant(boolean pdfaCompliant) { this.pdfaCompliant = pdfaCompliant; }
    public boolean isOcrDone() { return ocrDone; }
    public void setOcrDone(boolean ocrDone) { this.ocrDone = ocrDone; }
    public String getSignatureReport() { return signatureReport; }
    public void setSignatureReport(String signatureReport) { this.signatureReport = signatureReport; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
