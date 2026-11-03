package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Licznik numerów dziennika wpływu {@code DW rrrr/nnnnn} w roku (blokada wiersza jak przy sygnaturach). */
@Entity
@Table(name = "incoming_counter")
public class IncomingCounter {

    @Id
    @Column(name = "year")
    private int year;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected IncomingCounter() {
    }

    public IncomingCounter(int year, int lastValue) {
        this.year = year;
        this.lastValue = lastValue;
    }

    public int getYear() { return year; }
    public int getLastValue() { return lastValue; }
    public void setLastValue(int lastValue) { this.lastValue = lastValue; }
}
