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

import pl.ejtechnology.tk.eSkarga.domain.DeliveryMethod;

/** Doręczenie dokumentu (FR-25): adresat, kanał, numer nadania, data wysłania, data i sposób doręczenia; klucz idempotencji (D7). */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CaseDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_party_id")
    private Party addressee;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private DeliveryChannel channel;

    @Column(name = "dispatch_no", length = 80)
    private String dispatchNo;

    @Column(name = "sent_on")
    private LocalDate sentOn;

    @Column(name = "delivered_on")
    private LocalDate deliveredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private DeliveryMethod method;

    @Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Delivery() {
    }

    public Delivery(CaseDocument document, Party addressee, DeliveryChannel channel, String idempotencyKey) {
        this.document = document;
        this.addressee = addressee;
        this.channel = channel;
        this.idempotencyKey = idempotencyKey;
    }

    public void dispatched(String dispatchNo, LocalDate sentOn) {
        this.dispatchNo = dispatchNo;
        this.sentOn = sentOn;
    }

    /** Rejestracja zwrotki / UPD (UC-08). Zmiana daty to korekta wymagająca uzasadnienia i przeliczenia terminu (T-041). */
    public void delivered(LocalDate deliveredOn, DeliveryMethod method) {
        this.deliveredOn = deliveredOn;
        this.method = method;
    }

    public Long getId() { return id; }
    public CaseDocument getDocument() { return document; }
    public Party getAddressee() { return addressee; }
    public DeliveryChannel getChannel() { return channel; }
    public String getDispatchNo() { return dispatchNo; }
    public LocalDate getSentOn() { return sentOn; }
    public LocalDate getDeliveredOn() { return deliveredOn; }
    public DeliveryMethod getMethod() { return method; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
