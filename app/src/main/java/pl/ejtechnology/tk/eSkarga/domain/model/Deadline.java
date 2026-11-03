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

import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;

/** Termin wyliczony przez domenę z doręczenia (D6, FR-17); nigdy wpisywany ręcznie. */
@Entity
@Table(name = "deadline")
public class Deadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private DeadlineType kind;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "suspended_from")
    private LocalDate suspendedFrom;

    @Column(name = "suspended_to")
    private LocalDate suspendedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeadlineStatus status = DeadlineStatus.BIEGNIE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Deadline() {
    }

    public Deadline(CaseFile caseFile, Delivery delivery, DeadlineType kind, LocalDate startsOn, LocalDate endsOn) {
        this.caseFile = caseFile;
        this.delivery = delivery;
        this.kind = kind;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public void close() {
        this.status = DeadlineStatus.ZAKONCZONY;
    }

    public void revoke() {
        this.status = DeadlineStatus.UCHYLONY;
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public Delivery getDelivery() { return delivery; }
    public DeadlineType getKind() { return kind; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public LocalDate getSuspendedFrom() { return suspendedFrom; }
    public LocalDate getSuspendedTo() { return suspendedTo; }
    public DeadlineStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
