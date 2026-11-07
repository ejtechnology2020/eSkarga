package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Wymóg formalny listy kontrolnej w słowniku S-24 (Ts/Tw): kod, treść, podstawa, warunek stosowania, kolejność, stan, ostatnia zmiana. */
public record RequirementRow(Long id, String symbol, String kod, String wymog, String podstawa, String warunek, String warunekOpis, int kolejnosc,
                             boolean aktywny, String zmieniono) implements Serializable {

    public String rowClass() {
        return aktywny ? "" : "off";
    }
}
