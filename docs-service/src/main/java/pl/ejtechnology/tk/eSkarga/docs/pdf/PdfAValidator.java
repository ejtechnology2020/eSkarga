package pl.ejtechnology.tk.eSkarga.docs.pdf;

/** Walidacja PDF/A-2b (veraPDF) – każdy wygenerowany dokument i każdy skan po konwersji (NFR-11, T-031). */
public interface PdfAValidator {

    /** Wynik walidacji: zgodny z PDF/A-2b oraz lista naruszeń do raportu. */
    record Result(boolean compliant, java.util.List<String> violations) {
    }

    Result validate(byte[] pdf);
}
