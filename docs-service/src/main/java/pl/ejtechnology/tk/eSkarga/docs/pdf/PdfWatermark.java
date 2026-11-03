package pl.ejtechnology.tk.eSkarga.docs.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/** Znak wodny przy pobraniu dokumentu (brief §6, T-034): kto i kiedy pobrał, po przekątnej każdej strony, półprzezroczysty. */
public final class PdfWatermark {

    private final File fontFile;

    public PdfWatermark(File fontDir) {
        this.fontFile = fontDir == null ? null : new File(fontDir, "LiberationSans-Regular.ttf");
    }

    public byte[] apply(byte[] pdf, String text) {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.setAllSecurityToBeRemoved(true);
            PDFont font;
            String label = text;
            if (fontFile != null && fontFile.isFile()) {
                font = PDType0Font.load(doc, fontFile);
            } else {
                font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                label = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ł', 'l').replace('Ł', 'L');
            }
            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.25f);
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                float size = Math.max(18, Math.min(box.getWidth(), box.getHeight()) / 22);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gs);
                    cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
                    cs.beginText();
                    cs.setFont(font, size);
                    float textWidth = font.getStringWidth(label) / 1000 * size;
                    double angle = Math.atan2(box.getHeight(), box.getWidth());
                    float cx = box.getWidth() / 2 - (float) (Math.cos(angle) * textWidth / 2);
                    float cy = box.getHeight() / 2 - (float) (Math.sin(angle) * textWidth / 2);
                    cs.setTextMatrix(Matrix.getRotateInstance(angle, cx, cy));
                    cs.showText(label);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się nałożyć znaku wodnego", e);
        }
    }
}
