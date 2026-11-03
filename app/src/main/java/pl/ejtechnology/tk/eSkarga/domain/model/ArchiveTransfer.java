package pl.ejtechnology.tk.eSkarga.domain.model;

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

/** Przekazanie akt do archiwum zakładowego (FR-34, UC-21): numer spisu, kategoria, paczka z sumą kontrolną. */
@Entity
@Table(name = "archive_transfer")
public class ArchiveTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, length = 30, unique = true)
    private String transferNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "category", nullable = false, length = 10)
    private String category;

    @Column(name = "retention_until")
    private Integer retentionUntil;

    @Column(name = "pages", nullable = false)
    private int pages;

    @Column(name = "package_key", length = 400)
    private String packageKey;

    @Column(name = "package_sha256", columnDefinition = "CHAR(64)")
    private String packageSha256;

    @Column(name = "transferred_by", nullable = false, length = 120)
    private String transferredBy;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt = Instant.now();

    protected ArchiveTransfer() {
    }

    public ArchiveTransfer(String transferNo, CaseFile caseFile, String category, Integer retentionUntil, int pages, String packageKey,
                           String packageSha256, String transferredBy) {
        this.transferNo = transferNo;
        this.caseFile = caseFile;
        this.category = category;
        this.retentionUntil = retentionUntil;
        this.pages = pages;
        this.packageKey = packageKey;
        this.packageSha256 = packageSha256;
        this.transferredBy = transferredBy;
    }

    public Long getId() { return id; }
    public String getTransferNo() { return transferNo; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getCategory() { return category; }
    public Integer getRetentionUntil() { return retentionUntil; }
    public int getPages() { return pages; }
    public String getPackageKey() { return packageKey; }
    public String getPackageSha256() { return packageSha256; }
    public String getTransferredBy() { return transferredBy; }
    public Instant getTransferredAt() { return transferredAt; }
}
