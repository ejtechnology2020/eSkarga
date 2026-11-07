package pl.ejtechnology.tk.eSkarga.docs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Base64;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.convert.DocumentConverter;
import pl.ejtechnology.tk.eSkarga.docs.convert.OfficeFormatTest;

/** ADR 0026: DOCX w /dokumenty/przetworz → konwersja → ta sama ścieżka co PDF (podpisy, PDF/A, tekst); bez konwertera – błąd stanu. */
class DocumentsResourceOfficeTest {

    static DocsServices services;
    static DocumentsResource resource;

    @BeforeAll
    static void init() {
        services = new DocsServices();
        services.init();
        resource = new DocumentsResource();
        resource.services = services;
    }

    @Test
    void docxJestKonwertowanyIPrzetwarzanyJakPdf() throws IOException {
        services.converter(new DocumentConverter() {
            @Override
            public byte[] toPdf(byte[] content, String fileName) {
                assertThat(fileName).isEqualTo("dokument.docx");
                return services.pdf().renderPdfA("<html><body><p>Skarga konstytucyjna – treść z DOCX</p></body></html>", null);
            }

            @Override
            public boolean available() { return true; }
        });
        JsonObject r = resource.process(OfficeFormatTest.minimalDocx("Skarga"), true);
        assertThat(r.getString("skonwertowanoZ")).isEqualTo("docx");
        assertThat(r.getBoolean("ocrWykonany")).as("dokument z edytora ma warstwę tekstową – bez OCR").isFalse();
        assertThat(r.getString("tekst")).contains("treść z DOCX");
        assertThat(new String(Base64.getDecoder().decode(r.getString("pdf")), 0, 5)).isEqualTo("%PDF-");
        assertThat(r.getBoolean("pdfaZgodny")).isTrue();
    }

    @Test
    void bezKonwerteraCzytelnyBlad() throws IOException {
        services.converter(DocumentConverter.none());
        assertThatThrownBy(() -> resource.process(OfficeFormatTest.minimalDocx("x"), true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ESKARGA_DOCS_CONVERTERURL");
    }
}
