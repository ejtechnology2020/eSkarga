package pl.ejtechnology.tk.eSkarga.docs.convert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

public class OfficeFormatTest {

    /** Minimalny DOCX: archiwum ZIP z word/document.xml (do testów rozpoznania i konwersji). */
    public static byte[] minimalDocx(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"
                    + text + "</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void rozpoznajePoSygnaturze() throws IOException {
        assertThat(OfficeFormat.detect(minimalDocx("Skarga"))).isEqualTo("docx");
        byte[] ole = new byte[64];
        System.arraycopy(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}, 0, ole, 0, 8);
        assertThat(OfficeFormat.detect(ole)).isEqualTo("doc");
        assertThat(OfficeFormat.detect("%PDF-1.7 ...".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        ByteArrayOutputStream zipBezWord = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBezWord)) {
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThat(OfficeFormat.detect(zipBezWord.toByteArray())).as("ZIP bez word/ (np. XLSX) to nie dokument Word").isNull();
        assertThat(OfficeFormat.contentType("docx")).contains("wordprocessingml");
    }
}
