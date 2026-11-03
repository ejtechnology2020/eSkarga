package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Pismo niebędące skargą (S-12, FR-30). */
public record GeneralLetterRow(String nr, String nadawca, String sprawa, DeadlineView termin, String stan, Tone stanTone,
                               boolean wybrany, Tone wiersz) implements Serializable {

    public String rowClass() {
        if (wybrany) {
            return "sel";
        }
        return wiersz == null ? "" : wiersz.css();
    }
}
