package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;

/** Pozycja spisu akt (brief §4.2): numer karty, data, tytuł, rodzaj, stan podpisu, „nowy”. */
public record RecordPage(String id, String karty, LocalDate data, String tytul, DocumentKind rodzaj, String opis,
                         boolean nowy, boolean wybrany, boolean startTerminu, boolean pdf, String zrodloNazwa) implements Serializable {

    /** Dokument PDF bez oryginału Word (skany, pisma z szablonów, dane przykładowe). */
    public RecordPage(String id, String karty, LocalDate data, String tytul, DocumentKind rodzaj, String opis, boolean nowy, boolean wybrany, boolean startTerminu) {
        this(id, karty, data, tytul, rodzaj, opis, nowy, wybrany, startTerminu, true, null);
    }

    public boolean isNiejawny() {
        return rodzaj == DocumentKind.NIEJAWNY;
    }

    /** Dokument Word czekający na konwersję do PDF/A – bez podglądu (ADR 0026). */
    public boolean isCzekaNaKonwersje() {
        return !pdf;
    }

    public boolean isMaOryginal() {
        return pdf && zrodloNazwa != null;
    }
}
