package pl.ejtechnology.tk.eSkarga.domain.docket;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Licznik sygnatur {@code docket_counter(symbol, year, last_value)} – jedyna tabela repertorium z UPDATE (BR-01). */
@Entity
@Table(name = "docket_counter")
@IdClass(DocketCounter.Key.class)
public class DocketCounter {

    @Id
    @Column(name = "symbol", length = 3, nullable = false)
    private String symbol;

    @Id
    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected DocketCounter() {
    }

    public DocketCounter(String symbol, int year, int lastValue) {
        this.symbol = symbol;
        this.year = year;
        this.lastValue = lastValue;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getYear() {
        return year;
    }

    public int getLastValue() {
        return lastValue;
    }

    public void setLastValue(int lastValue) {
        this.lastValue = lastValue;
    }

    /** Klucz złożony licznika. */
    public static class Key implements Serializable {
        private String symbol;
        private int year;

        public Key() {
        }

        public Key(String symbol, int year) {
            this.symbol = symbol;
            this.year = year;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && year == k.year && Objects.equals(symbol, k.symbol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbol, year);
        }
    }
}
