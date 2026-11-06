package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Wersja szablonu pisma FreeMarker (D13, T-026a): projekt → aktywna (akceptacja drugiej osoby) → wycofana. */
@Entity
@Table(name = "letter_template", uniqueConstraints = @UniqueConstraint(name = "uq_letter_template", columnNames = {"code", "version"}))
public class LetterTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateStatus status = TemplateStatus.PROJEKT;

    @Column(name = "source_xhtml", nullable = false, columnDefinition = "TEXT")
    private String sourceXhtml;

    @Column(name = "author_user_id", nullable = false, length = 120)
    private String authorUserId;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LetterTemplate() {
    }

    public LetterTemplate(String code, String name, int version, String sourceXhtml, String authorUserId) {
        this.code = code;
        this.name = name;
        this.version = version;
        this.sourceXhtml = sourceXhtml;
        this.authorUserId = authorUserId;
    }

    /** Aktywacja wymaga innej osoby niż autor (zasada dwóch osób, T-026a). */
    public void activate(String approver) {
        if (approver == null || approver.equals(authorUserId)) {
            throw new IllegalArgumentException("Aktywacja szablonu wymaga akceptacji drugiej osoby");
        }
        this.status = TemplateStatus.AKTYWNA;
        this.approvedBy = approver;
    }

    public void withdraw() {
        this.status = TemplateStatus.WYCOFANA;
    }

    /** Poprawka projektu w miejscu (tylko PROJEKT); wersja aktywna lub wycofana jest niezmienna – zmiana to nowa wersja. */
    public void updateDraft(String name, String sourceXhtml) {
        if (status != TemplateStatus.PROJEKT) {
            throw new IllegalStateException("Zmienić można tylko projekt szablonu – dla wersji " + status.name().toLowerCase() + " zapisz nową wersję");
        }
        this.name = name;
        this.sourceXhtml = sourceXhtml;
    }

    /** Usunięcie logiczne: nigdy wersji aktywnej (najpierw wycofanie). Wiersz zostaje, bo pisma pamiętają wersję szablonu (D13). */
    public void markDeleted() {
        if (status == TemplateStatus.AKTYWNA) {
            throw new IllegalStateException("Aktywnej wersji nie można usunąć – najpierw ją wycofaj albo aktywuj inną");
        }
        this.status = TemplateStatus.USUNIETA;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public TemplateStatus getStatus() { return status; }
    public String getSourceXhtml() { return sourceXhtml; }
    public String getAuthorUserId() { return authorUserId; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
