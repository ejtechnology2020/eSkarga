package pl.ejtechnology.tk.eSkarga.docs.ocr;

/** OCR skanów (T-031, NFR-11): warstwa tekstowa po polsku + konwersja do PDF/A-2b. */
public interface OcrService {

    /** Wynik: dokument (po OCR albo oryginał), czy OCR wykonano, uwaga do metadanych. */
    record Result(byte[] pdf, boolean ocrDone, String note) {
    }

    Result ocr(byte[] pdf);

    boolean available();

    /** OCRmyPDF gdy jest w PATH, inaczej zaślepka, która oznacza dokument jako „bez OCR”. */
    static OcrService detect() {
        OcrMyPdfCli cli = new OcrMyPdfCli();
        return cli.available() ? cli : new NoOpOcr("brak ocrmypdf w PATH");
    }
}
