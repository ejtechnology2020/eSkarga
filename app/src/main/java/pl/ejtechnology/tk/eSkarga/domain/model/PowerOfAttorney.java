package pl.ejtechnology.tk.eSkarga.domain.model;

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

/** Pełnomocnictwo (art. 44, art. 53 ust. 2 pkt 3) – podstawa weryfikacji w liście kontrolnej (BR-05, FR-13). */
@Entity
@Table(name = "power_of_attorney")
public class PowerOfAttorney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private PowerOfAttorneyKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "profession", length = 30)
    private Profession profession;

    @Column(name = "registry_no", length = 60)
    private String registryNo;

    @Column(name = "granted_on")
    private LocalDate grantedOn;

    @Column(name = "scope", length = 400)
    private String scope;

    protected PowerOfAttorney() {
    }

    public PowerOfAttorney(Party party, PowerOfAttorneyKind kind, Profession profession, String registryNo, LocalDate grantedOn, String scope) {
        this.party = party;
        this.kind = kind;
        this.profession = profession;
        this.registryNo = registryNo;
        this.grantedOn = grantedOn;
        this.scope = scope;
    }

    public Long getId() { return id; }
    public Party getParty() { return party; }
    public PowerOfAttorneyKind getKind() { return kind; }
    public Profession getProfession() { return profession; }
    public String getRegistryNo() { return registryNo; }
    public LocalDate getGrantedOn() { return grantedOn; }
    public String getScope() { return scope; }
}
