package pl.ejtechnology.tk.eSkarga.docs.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.support.TestPdfs;

/** Brief §6 / T-034: znak wodny „kto i kiedy pobrał” na każdej stronie. */
class WatermarkTest {

    @Test
    void nakladaTekstNaKazdaStrone() throws Exception {
        byte[] pdf = TestPdfs.withText("Karta 1");
        byte[] marked = new PdfWatermark(new File("/usr/share/fonts/truetype/liberation")).apply(pdf, "Pobrał: Jan Testowy, 03.11.2026 10:15, Ts 312/26");
        assertThat(TestPdfs.pages(marked)).isEqualTo(1);
        String text = TestPdfs.text(marked).replaceAll("\\s+", "");
        assertThat(text).contains("Karta1").contains("Ts312/26");
        byte[] noFont = new PdfWatermark(null).apply(pdf, "Pobrał: Łukasz Żółty");
        assertThat(TestPdfs.text(noFont).replaceAll("\\s+", "")).contains("Pobral:LukaszZolty");
    }
}
