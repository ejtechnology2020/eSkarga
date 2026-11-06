package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Szablon pisma z wersją i stanem (S-24, D13): id wersji i kod do akcji (podgląd, edycja, aktywacja, wycofanie, usunięcie), liczba pism z tej wersji. */
public record TemplateRow(Long id, String kod, String nazwa, String rodzaj, String wersja, String zmieniono, String stan, Tone stanTone,
                          boolean wybrany, long uzycia) implements Serializable {

    public boolean isProjekt() { return "projekt".equals(stan); }
    public boolean isAktywna() { return "aktywna".equals(stan) || "obowiązuje".equals(stan); }

    public String rowClass() {
        return wybrany ? "sel" : (stanTone == Tone.OFF ? "off" : "");
    }
}
