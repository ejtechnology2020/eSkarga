package pl.ejtechnology.tk.eSkarga.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/** Rozpoznanie plików po sygnaturze (T-055, ADR 0026): PDF, TIFF, DOCX (ZIP z word/), DOC (OLE2); inne – odrzucone. */
public class FileTypesTest {

    public static byte[] docx(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void rozpoznajeTypy() throws IOException {
        assertThat(FileTypes.detect("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo(FileTypes.PDF);
        assertThat(FileTypes.detect(new byte[] {'I', 'I', 42, 0, 1, 2, 3, 4})).isEqualTo(FileTypes.TIFF);
        assertThat(FileTypes.detect(docx("Skarga"))).isEqualTo(FileTypes.DOCX);
        byte[] ole = new byte[32];
        System.arraycopy(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}, 0, ole, 0, 8);
        assertThat(FileTypes.detect(ole)).isEqualTo(FileTypes.DOC);
        assertThat(FileTypes.detect("PK\u0003\u0004 bez word".getBytes(StandardCharsets.ISO_8859_1))).as("ZIP bez word/ to nie DOCX").isNull();
        assertThat(FileTypes.detect("<html>".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(FileTypes.isOffice(FileTypes.DOCX)).isTrue();
        assertThat(FileTypes.isCaseDocument(FileTypes.TIFF)).as("TIFF zostaje skanem wpływu, nie kartą akt").isFalse();
        assertThat(FileTypes.extension(FileTypes.DOC)).isEqualTo("doc");
        assertThat(FileTypes.label(FileTypes.DOCX)).isEqualTo("DOCX");
    }
}
