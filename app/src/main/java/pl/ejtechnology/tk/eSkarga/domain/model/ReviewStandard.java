package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Słownik wzorców kontroli (przepisy Konstytucji). */
@Entity
@Table(name = "review_standard")
public class ReviewStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "provision", nullable = false, length = 200)
    private String provision;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ReviewStandard() {
    }

    public ReviewStandard(String code, String provision, String description) {
        this.code = code;
        this.provision = provision;
        this.description = description;
    }

    public void withdraw() { this.active = false; }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getProvision() { return provision; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
