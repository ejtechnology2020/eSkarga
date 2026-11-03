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

/** Zaznaczenie w dokumencie (T-035): współrzędne jako JSON rysowane jako nakładka; widoczne tylko dla zespołu sędziego. */
@Entity
@Table(name = "highlight")
public class Highlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CaseDocument document;

    @Column(name = "author_user_id", nullable = false, length = 120)
    private String authorUserId;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(name = "geometry_json", nullable = false, length = 4000)
    private String geometryJson;

    @Column(name = "comment_text", length = 2000)
    private String commentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Highlight() {
    }

    public Highlight(CaseDocument document, String authorUserId, int pageNo, String geometryJson, String commentText) {
        this.document = document;
        this.authorUserId = authorUserId;
        this.pageNo = pageNo;
        this.geometryJson = geometryJson;
        this.commentText = commentText;
    }

    public Long getId() { return id; }
    public CaseDocument getDocument() { return document; }
    public String getAuthorUserId() { return authorUserId; }
    public int getPageNo() { return pageNo; }
    public String getGeometryJson() { return geometryJson; }
    public String getCommentText() { return commentText; }
    public Instant getCreatedAt() { return createdAt; }
}
