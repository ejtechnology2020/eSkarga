package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dzień wolny w Trybunale poza świętami ustawowymi (kalendarz terminów, S-24). */
@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Holiday() {
    }

    public Holiday(LocalDate day, String description, String createdBy) {
        this.day = day;
        this.description = description;
        this.createdBy = createdBy;
    }

    public LocalDate getDay() { return day; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
