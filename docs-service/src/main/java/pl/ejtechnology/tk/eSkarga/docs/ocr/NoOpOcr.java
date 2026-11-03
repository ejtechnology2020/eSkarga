package pl.ejtechnology.tk.eSkarga.docs.ocr;

/** Zaślepka: zwraca dokument bez zmian i oznacza brak OCR (kolejka do ponowienia, gdy OCR będzie dostępny). */
public final class NoOpOcr implements OcrService {

    private final String reason;

    public NoOpOcr(String reason) {
        this.reason = reason;
    }

    @Override
    public Result ocr(byte[] pdf) {
        return new Result(pdf, false, "OCR pominięty: " + reason);
    }

    @Override
    public boolean available() {
        return false;
    }
}
