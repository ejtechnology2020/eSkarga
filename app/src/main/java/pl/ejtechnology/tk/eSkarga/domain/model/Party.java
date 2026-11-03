package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

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

/** Strona / uczestnik postępowania z adresem do doręczeń (FR-26). Dane osobowe – maskowane na listach (NFR-05). */
@Entity
@Table(name = "party")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_file_id", nullable = false)
    private CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private PartyRole role;

    @Column(name = "display_name", nullable = false, length = 400)
    private String displayName;

    @Column(name = "delivery_address", length = 600)
    private String deliveryAddress;

    @Column(name = "e_delivery_address", length = 120)
    private String eDeliveryAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Party() {
    }

    public Party(CaseFile caseFile, PartyRole role, String displayName, String deliveryAddress, String eDeliveryAddress) {
        this.caseFile = caseFile;
        this.role = role;
        this.displayName = displayName;
        this.deliveryAddress = deliveryAddress;
        this.eDeliveryAddress = eDeliveryAddress;
    }

    /** Inicjały do widoku zanonimizowanego (BR-09). */
    public String initials() {
        StringBuilder sb = new StringBuilder();
        for (String p : displayName.replaceAll("^(adw\\.|r\\.pr\\.)\\s*", "").split("\\s+")) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    public Long getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public PartyRole getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getEDeliveryAddress() { return eDeliveryAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
