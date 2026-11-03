package pl.ejtechnology.tk.eSkarga.docs.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T-031: OCR przez OCRmyPDF, gdy dostępny; inaczej zaślepka oznaczająca brak OCR (dokument do ponowienia). */
class OcrServiceTest {

    @TempDir
    Path tmp;

    @Test
    void brakBinarkiDajeZaslepke() {
        OcrMyPdfCli missing = new OcrMyPdfCli("ocrmypdf-nie-istnieje-" + System.nanoTime(), tmp, 5);
        assertThat(missing.available()).isFalse();
        OcrService.Result r = missing.ocr("%PDF-1.4".getBytes());
        assertThat(r.ocrDone()).isFalse();
        assertThat(r.pdf()).isEqualTo("%PDF-1.4".getBytes());
        assertThat(r.note()).contains("OCR");
        OcrService detected = OcrService.detect();
        assertThat(detected).isNotNull();
        assertThat(new NoOpOcr("test").ocr(new byte[]{1}).ocrDone()).isFalse();
    }
}
