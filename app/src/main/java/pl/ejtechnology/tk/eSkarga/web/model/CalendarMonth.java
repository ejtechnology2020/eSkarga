package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.util.List;

/** Miesiąc kalendarza dni wolnych (S-24). */
public record CalendarMonth(String name, List<Cell> cells) implements Serializable {

    /** Komórka dnia: pusta na początku miesiąca, weekend, święto ustawowe, dzień wolny w Trybunale. */
    public record Cell(String day, String cls, String title) implements Serializable {
    }
}
