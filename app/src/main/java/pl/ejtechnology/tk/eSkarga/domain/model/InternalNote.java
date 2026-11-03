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

/** Notatka wewnętrzna zespołu sędziego (FR-24, T-035) – nigdy w aktach jawnych ani w eksporcie. */
@Entity
@Table(name = "internal_note")
public class InternalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "author_user_id", nullable = false, length = 120)
    private String authorUserId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected InternalNote() {
    }

    public InternalNote(CaseFile caseFile, String authorUserId, String title, String body) {
        this.caseFile = caseFile;
        this.authorUserId = authorUserId;
        this.title = title;
        this.body = body;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getAuthorUserId() { return authorUserId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
