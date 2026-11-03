package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Wiersz tabeli zadań na pulpicie (S-01, UC-18). */
public record TaskRow(String id, CaseSummary sprawa, String tytul, String opis, String wRoli, DeadlineView termin,
                      LocalDate czekaOd, String akcja, String akcjaUrl, boolean akcjaGlowna, boolean grupowe, Tone wiersz)
        implements Serializable {

    public String rowClass() {
        return wiersz == null ? "" : wiersz.css();
    }
}
