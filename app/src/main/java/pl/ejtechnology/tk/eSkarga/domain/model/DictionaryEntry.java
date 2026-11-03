package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Wspólna postać pozycji słownika (FR-35): kod, nazwa, aktywność. Pozycji nie usuwa się – wycofuje. */
@MappedSuperclass
public abstract class DictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected DictionaryEntry() {
    }

    protected DictionaryEntry(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void withdraw() {
        this.active = false;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
}
