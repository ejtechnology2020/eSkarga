package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;

/** Pozycja spisu akt (brief §4.2): numer karty, data, tytuł, rodzaj, stan podpisu, „nowy”. */
public record RecordPage(String id, String karty, LocalDate data, String tytul, DocumentKind rodzaj, String opis,
                         boolean nowy, boolean wybrany, boolean startTerminu) implements Serializable {

    public boolean isNiejawny() {
        return rodzaj == DocumentKind.NIEJAWNY;
    }
}
