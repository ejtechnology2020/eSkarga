package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Pozycja słownika (S-24, FR-35). */
public record DictionaryRow(String kod, String przepis, String opis, int uzycia, boolean aktywna, boolean wybrany)
        implements Serializable {

    public String rowClass() {
        return wybrany ? "sel" : (aktywna ? "" : "off");
    }
}
