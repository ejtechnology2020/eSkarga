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

import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;

/** Pismo wpływające – wpis dziennika wpływu (FR-01 – FR-03, UC-01). Wpis nie jest aktualizowany poza kwalifikacją. */
@Entity
@Table(name = "incoming_letter")
// rola aplikacji ma UPDATE tylko na kolumnach qualification i case_file_id (V5.1, UC-02) – Hibernate aktualizuje wyłącznie zmienione kolumny
@org.hibernate.annotations.DynamicUpdate
public class IncomingLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "register_no", nullable = false, length = 20, unique = true)
    private String registerNo;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "filed_on")
    private LocalDate filedOn;

    @Column(name = "filed_on_uncertain", nullable = false)
    private boolean filedOnUncertain;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private IntakeChannel channel;

    @Column(name = "sender", nullable = false, length = 400)
    private String sender;

    @Column(name = "attachments", nullable = false)
    private int attachments;

    @Column(name = "copies", nullable = false)
    private int copies;

    @Column(name = "classified", nullable = false)
    private boolean classified;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification", length = 30)
    private LetterQualification qualification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_file_id")
    private CaseFile caseFile;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IncomingLetter() {
    }

    public IncomingLetter(String registerNo, Instant receivedAt, LocalDate filedOn, boolean filedOnUncertain, IntakeChannel channel,
                          String sender, int attachments, int copies, boolean classified, String createdBy) {
        this.registerNo = registerNo;
        this.receivedAt = receivedAt;
        this.filedOn = filedOn;
        this.filedOnUncertain = filedOnUncertain;
        this.channel = channel;
        this.sender = sender;
        this.attachments = attachments;
        this.copies = copies;
        this.classified = classified;
        this.createdBy = createdBy;
    }

    /** Kwalifikacja przez referenta (UC-02) – jedyna dozwolona zmiana wpisu. */
    public void qualify(LetterQualification qualification, CaseFile caseFile) {
        this.qualification = qualification;
        this.caseFile = caseFile;
    }

    public Long getId() { return id; }
    public String getRegisterNo() { return registerNo; }
    public Instant getReceivedAt() { return receivedAt; }
    public LocalDate getFiledOn() { return filedOn; }
    public boolean isFiledOnUncertain() { return filedOnUncertain; }
    public IntakeChannel getChannel() { return channel; }
    public String getSender() { return sender; }
    public int getAttachments() { return attachments; }
    public int getCopies() { return copies; }
    public boolean isClassified() { return classified; }
    public LetterQualification getQualification() { return qualification; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
