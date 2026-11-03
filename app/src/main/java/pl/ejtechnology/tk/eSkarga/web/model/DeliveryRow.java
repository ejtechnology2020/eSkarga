package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Przesyłka w ekspedycji (S-10): do wysłania, bez potwierdzenia albo doręczona. */
public record DeliveryRow(DocketNumber sprawa, String dokument, String adresat, String adresatUwaga, String kanal,
                          LocalDate data, String sposob, DeadlineView termin, String akcja, boolean akcjaGlowna,
                          boolean zaznaczona, Tone wiersz) implements Serializable {

    public String rowClass() {
        if (zaznaczona) {
            return "sel";
        }
        return wiersz == null ? "" : wiersz.css();
    }
}
