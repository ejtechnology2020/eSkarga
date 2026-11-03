package pl.ejtechnology.tk.eSkarga.docs.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.support.TestPdfs;
import pl.ejtechnology.tk.eSkarga.docs.templates.FreemarkerTemplateRenderer;
import pl.ejtechnology.tk.eSkarga.docs.templates.SampleLetterModel;
import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateCatalog;

/** T-026: każdy szablon domyślny daje PDF zgodny z PDF/A-2b (veraPDF), z polskimi znakami, w czasie poniżej 2 s. */
class VeraPdfTemplatesTest {

    static final File FONTS = new File(System.getProperty("eskarga.pdf.fontDir", "/usr/share/fonts/truetype/liberation"));
    static final VeraPdfValidator VALIDATOR = new VeraPdfValidator();

    static List<String> codes() {
        return TemplateCatalog.codes();
    }

    @ParameterizedTest
    @MethodSource("codes")
    void szablonDajePdfA2b(String code) throws Exception {
        assumeTrue(new File(FONTS, "LiberationSerif-Regular.ttf").isFile(), "brak czcionek Liberation – pomijam");
        String xhtml = new FreemarkerTemplateRenderer().render(code, TemplateCatalog.source(code), SampleLetterModel.ts312());
        assertThat(xhtml).contains("2026").doesNotContain("${");
        long start = System.nanoTime();
        byte[] pdf = new OpenHtmlToPdfRenderer(FONTS).renderPdfA(xhtml, null);
        PdfAValidator.Result result = VALIDATOR.validate(pdf);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(result.violations()).as("naruszenia PDF/A dla " + code).isEmpty();
        assertThat(result.compliant()).isTrue();
        assertThat(TestPdfs.text(pdf)).contains("2026");
        assertThat(ms).as("render + walidacja " + code + " [ms]").isLessThan(10_000);
    }

    @Test
    void zwyklyPdfBezOsadzonychCzcionekNieJestPdfA() throws Exception {
        PdfAValidator.Result r = VALIDATOR.validate(TestPdfs.withText("Zwykly dokument"));
        assertThat(r.compliant()).isFalse();
        assertThat(r.violations()).isNotEmpty();
    }

    @Test
    void smieciNieSaPdf() {
        PdfAValidator.Result r = VALIDATOR.validate("to nie jest pdf".getBytes());
        assertThat(r.compliant()).isFalse();
    }
}
