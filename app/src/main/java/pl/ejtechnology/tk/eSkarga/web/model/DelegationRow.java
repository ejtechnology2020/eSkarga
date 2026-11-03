package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Upoważnienie (S-23, BR-10, FR-36). */
public record DelegationRow(String id, String osoba, String zakres, LocalDate od, LocalDate doDnia, String akt,
                            String wprowadzil, String status, Tone statusTone, String statusIcon, boolean wybrany, Tone wiersz)
        implements Serializable {

    public String rowClass() {
        if (wybrany) {
            return "sel";
        }
        return wiersz == null ? "" : wiersz.css();
    }
}
