package pl.ejtechnology.tk.eSkarga.docs.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * OpenHTMLtoPDF (artefakt {@code io.github.openhtmltopdf}, pakiety {@code com.openhtmltopdf}) w trybie PDF/A-2b. Czcionka z polskimi znakami jest osadzana
 * (dev: Liberation Serif z katalogu wskazanego w {@code eskarga.pdf.fontDir}); czcionka urzędowa TK – do ustalenia
 * licencja na osadzanie (otwarta kwestia architektury).
 */
public final class OpenHtmlToPdfRenderer implements PdfRenderer {

    private final File fontDir;

    public OpenHtmlToPdfRenderer(File fontDir) {
        this.fontDir = fontDir;
    }

    @Override
    public byte[] renderPdfA(String xhtml, String baseUri) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B);
            builder.withHtmlContent(xhtml, baseUri);
            if (fontDir != null && fontDir.isDirectory()) {
                File regular = new File(fontDir, "LiberationSerif-Regular.ttf");
                File bold = new File(fontDir, "LiberationSerif-Bold.ttf");
                if (regular.isFile()) {
                    builder.useFont(regular, "Liberation Serif", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                }
                if (bold.isFile()) {
                    builder.useFont(bold, "Liberation Serif", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
                }
            }
            builder.toStream(out);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.createPDF();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się wygenerować PDF/A", e);
        }
    }
}
