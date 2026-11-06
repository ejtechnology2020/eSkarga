package pl.ejtechnology.tk.eSkarga.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;

/** S-08: wyciąg roczny z repertorium do PDF – wpisy, nagłówek, znaki polskie (z czcionką Liberation, gdy jest w systemie). */
class RepertoriumPdfTest {

    @Test
    void wyciagZawieraWpisyINaglowek() throws Exception {
        CaseSummary ts1 = new CaseSummary(new DocketNumber(DocketSymbol.TS, 1, 2026), "skarga konstytucyjna", CaseStatus.ZAREJESTROWANA, "Jan Przykładowy",
                "adw. Anna Testowa", null, Set.of(), DeadlineView.none("brak"), null, "art. 1 ustawy przykładowej", LocalDate.of(2026, 11, 5), null, null, "v1");
        File fonts = RepertoriumPdf.defaultFontDir();
        byte[] pdf = new RepertoriumPdf(fonts, s -> "Zarejestrowana").render(DocketSymbol.TS, 2026, List.of(ts1), LocalDate.of(2026, 11, 6), "Referent Testowy");
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(text).contains("Repertorium Ts", "2026", "Ts 1/26", "05.11.2026", "Zarejestrowana", "Referent Testowy", "Strona 1 z 1");
            if (new File(fonts, "LiberationSerif-Regular.ttf").isFile()) {
                assertThat(text).contains("Jan Przykładowy", "Skarżący");
            }
        }
    }

    @Test
    void pustyRokDajeDokumentZInformacja() throws Exception {
        byte[] pdf = new RepertoriumPdf(RepertoriumPdf.defaultFontDir(), s -> s).render(DocketSymbol.TW, 2025, List.of(), LocalDate.of(2026, 11, 6), "x");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Brak wpis");
        }
    }
}
