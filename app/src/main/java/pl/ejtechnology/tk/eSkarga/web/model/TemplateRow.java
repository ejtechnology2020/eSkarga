package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Szablon pisma z wersją i stanem (S-24, D13). */
public record TemplateRow(String nazwa, String rodzaj, String wersja, String zmieniono, String stan, Tone stanTone,
                          boolean wybrany) implements Serializable {

    public String rowClass() {
        return wybrany ? "sel" : (stanTone == Tone.OFF ? "off" : "");
    }
}
