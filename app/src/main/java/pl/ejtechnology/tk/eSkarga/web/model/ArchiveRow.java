package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Sprawa zamknięta do przekazania do archiwum (S-21, UC-21). */
public record ArchiveRow(DocketNumber sprawa, String zakonczenie, String kategoria, String przechowywanie, String kompletnosc,
                         Tone kompletnoscTone, boolean dostepna, boolean zaznaczona, int karty) implements Serializable {

    public String rowClass() {
        if (!dostepna) {
            return "off";
        }
        if (kompletnoscTone == Tone.ERR) {
            return "err";
        }
        return zaznaczona ? "sel" : "";
    }
}
