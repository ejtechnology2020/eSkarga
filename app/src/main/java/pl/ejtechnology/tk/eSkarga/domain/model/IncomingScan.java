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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Skan pisma dołączony przy rejestracji wpływu (S-03, FR-03): plik w repozytorium S3 (D5), tu metadane i SHA-256.
 * Po założeniu sprawy z pisma skany są przenoszone do akt jako {@link CaseDocument} (UC-03); dokument z klauzulą nie ma skanu (NFR-06).
 */
@Entity
@Table(name = "incoming_scan")
public class IncomingScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "letter_id", nullable = false)
    private IncomingLetter letter;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected IncomingScan() {
    }

    public IncomingScan(IncomingLetter letter, String fileName, String contentType, long sizeBytes, String sha256, String storageKey, String uploadedBy, Instant uploadedAt) {
        this.letter = letter;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public IncomingLetter getLetter() { return letter; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
