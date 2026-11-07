package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pl.ejtechnology.tk.eSkarga.domain.JudgeFunction;

/**
 * Słownik sędziów (S-24, ADR 0027): login z ID-users, nazwa do pism, funkcja (Prezes, Wiceprezes, sędzia) i kadencja. Źródło listy
 * sędziów do wyznaczenia (S-17) i składów (S-18): sędzia poza kadencją albo wycofany nie może być wyznaczony. Wpis wycofuje się,
 * nie usuwa (sprawy i zarządzenia odwołują się do loginu).
 */
@Entity
@Table(name = "judge")
public class Judge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100, unique = true)
    private String userId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "function_code", nullable = false, length = 20)
    private JudgeFunction function = JudgeFunction.SEDZIA;

    @Column(name = "term_from", nullable = false)
    private LocalDate termFrom;

    /** Koniec kadencji (9 lat, art. 194 ust. 1 Konstytucji); {@code null} = nieznany. */
    @Column(name = "term_to")
    private LocalDate termTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Judge() {
    }

    public Judge(String userId, String displayName, JudgeFunction function, LocalDate termFrom, LocalDate termTo, String createdBy, Instant now) {
        this.userId = userId;
        this.displayName = displayName;
        this.function = function;
        this.termFrom = termFrom;
        this.termTo = termTo;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void update(String displayName, JudgeFunction function, LocalDate termFrom, LocalDate termTo, String by, Instant now) {
        this.displayName = displayName;
        this.function = function;
        this.termFrom = termFrom;
        this.termTo = termTo;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public void setActive(boolean active, String by, Instant now) {
        this.active = active;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    /** Czy w dniu {@code day} sędzia może być wyznaczony: wpis aktywny i dzień w kadencji. */
    public boolean eligibleOn(LocalDate day) {
        return active && !day.isBefore(termFrom) && (termTo == null || !day.isAfter(termTo));
    }

    /** Nazwa do pism i list: „sędzia TK Jan Nowak”, „Prezes TK …” – gdy nazwa nie zawiera już tytułu. */
    public String titledName() {
        String n = displayName == null ? userId : displayName;
        String lower = n.toLowerCase();
        if (lower.startsWith("sędzia") || lower.startsWith("prezes") || lower.startsWith("wiceprezes")) {
            return n;
        }
        return function.label() + " " + n;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public JudgeFunction getFunction() { return function; }
    public LocalDate getTermFrom() { return termFrom; }
    public LocalDate getTermTo() { return termTo; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
