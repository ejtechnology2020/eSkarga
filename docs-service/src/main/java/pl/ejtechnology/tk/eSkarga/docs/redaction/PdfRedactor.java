package pl.ejtechnology.tk.eSkarga.docs.redaction;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Redakcja po stronie serwera (T-036, FR-22): każda strona jest rastrowana, zaczernione pola nakładane na obraz,
 * a wynik składany jako nowy PDF z samych obrazów – usunięte dane nie istnieją w pliku (brak warstwy tekstowej,
 * metadanych i załączników oryginału). Warstwę OCR dodaje się ponownie dopiero po kontroli człowieka.
 */
public final class PdfRedactor {

    /** Pole do zaczernienia w punktach PDF (początek układu w lewym dolnym rogu, jak w PDF). */
    public record Box(int page, float x, float y, float width, float height) {
    }

    private final float dpi;

    public PdfRedactor() {
        this(150f);
    }

    public PdfRedactor(float dpi) {
        this.dpi = dpi;
    }

    public byte[] redact(byte[] pdf, List<Box> boxes) {
        try (PDDocument src = Loader.loadPDF(pdf); PDDocument out = new PDDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            src.setAllSecurityToBeRemoved(true);
            PDFRenderer renderer = new PDFRenderer(src);
            float scale = dpi / 72f;
            for (int i = 0; i < src.getNumberOfPages(); i++) {
                PDPage srcPage = src.getPage(i);
                PDRectangle media = srcPage.getMediaBox();
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.BLACK);
                for (Box b : boxes) {
                    if (b.page() == i + 1) {
                        int px = Math.round(b.x() * scale);
                        int py = Math.round((media.getHeight() - b.y() - b.height()) * scale);
                        g.fillRect(px, py, Math.round(b.width() * scale), Math.round(b.height() * scale));
                    }
                }
                g.dispose();
                PDPage page = new PDPage(new PDRectangle(media.getWidth(), media.getHeight()));
                out.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(out, img);
                try (PDPageContentStream cs = new PDPageContentStream(out, page)) {
                    cs.drawImage(image, 0, 0, media.getWidth(), media.getHeight());
                }
            }
            out.save(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się zanonimizować dokumentu", e);
        }
    }
}
