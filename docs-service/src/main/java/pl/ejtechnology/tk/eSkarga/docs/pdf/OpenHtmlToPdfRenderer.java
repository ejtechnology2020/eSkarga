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
            // PDF/A-2b wymaga OutputIntent z profilem RGB (ISO 19005-2 6.2.4.3) – profil sRGB z JDK
            builder.useColorProfile(java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData());
            builder.withHtmlContent(xhtml, baseUri);
            File regular = fontDir == null ? null : new File(fontDir, "LiberationSerif-Regular.ttf");
            if (regular == null || !regular.isFile()) {
                // PDF/A wymaga osadzonych czcionek; bez nich OpenHTMLtoPDF zgłasza błąd wewnętrzny – komunikat ma wskazać konfigurację
                throw new IllegalStateException("Brak czcionki LiberationSerif-Regular.ttf w katalogu " + fontDir
                        + " – zainstaluj pakiet fonts-liberation albo ustaw eskarga.pdf.fontDir (ESKARGA_PDF_FONTDIR)");
            }
            {
                File bold = new File(fontDir, "LiberationSerif-Bold.ttf");
                if (regular.isFile()) {
                    builder.useFont(regular, "Liberation Serif", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                    // rodzina ogólna „serif” (np. pola marginesów @page) też musi być osadzona – PDF/A nie dopuszcza czcionek systemowych
                    builder.useFont(regular, "serif", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                }
                if (bold.isFile()) {
                    builder.useFont(bold, "Liberation Serif", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
                    builder.useFont(bold, "serif", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
                }
                File italic = new File(fontDir, "LiberationSerif-Italic.ttf");
                if (italic.isFile()) {
                    builder.useFont(italic, "Liberation Serif", 400, BaseRendererBuilder.FontStyle.ITALIC, true);
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
