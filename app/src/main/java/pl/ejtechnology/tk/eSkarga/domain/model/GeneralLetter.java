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

/** Pismo niebędące skargą (FR-30, UC-17) z terminem odpowiedzi i odpowiedzią z szablonu. */
@Entity
@Table(name = "general_letter")
public class GeneralLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "letter_id", nullable = false)
    private IncomingLetter letter;

    @Column(name = "subject", length = 600)
    private String subject;

    @Column(name = "reply_due_on")
    private LocalDate replyDueOn;

    @Column(name = "replied_on")
    private LocalDate repliedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_document_id")
    private CaseDocument replyDocument;

    @Column(name = "template_code", length = 60)
    private String templateCode;

    protected GeneralLetter() {
    }

    public GeneralLetter(IncomingLetter letter, String subject, LocalDate replyDueOn) {
        this.letter = letter;
        this.subject = subject;
        this.replyDueOn = replyDueOn;
    }

    public void replied(LocalDate on, String templateCode, CaseDocument replyDocument) {
        this.repliedOn = on;
        this.templateCode = templateCode;
        this.replyDocument = replyDocument;
    }

    public Long getId() { return id; }
    public IncomingLetter getLetter() { return letter; }
    public String getSubject() { return subject; }
    public LocalDate getReplyDueOn() { return replyDueOn; }
    public LocalDate getRepliedOn() { return repliedOn; }
    public CaseDocument getReplyDocument() { return replyDocument; }
    public String getTemplateCode() { return templateCode; }
}
