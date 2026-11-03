package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

import pl.ejtechnology.tk.eSkarga.domain.CaseFlag;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Wiersz rejestru zażaleń (S-11, UC-11). Ocena terminowości jest pomocnicza – ocenia skład. */
public record AppealRow(DocketNumber sprawa, Set<CaseFlag> flagi, LocalDate doreczenieOdmowy, LocalDate wniesienie,
                        String wniesienieUwaga, DeadlineView terminowosc, String terminowoscTag, Tone terminowoscTone,
                        String sklad, String posiedzenie, DeadlineView posiedzenieTermin, String wynik, Tone wynikTone,
                        String akcja, String akcjaUrl, boolean wybrany, Tone wiersz) implements Serializable {

    public String rowClass() {
        if (wybrany) {
            return "sel";
        }
        return wiersz == null ? "" : wiersz.css();
    }
}
