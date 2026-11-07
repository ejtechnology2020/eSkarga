package pl.ejtechnology.tk.eSkarga.docs.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Klient Gotenberga: żądanie multipart z plikiem i polem pdfa, odpowiedź PDF; błędy konwertera jako stan (409), nie 500. */
class GotenbergConverterTest {

    static HttpServer server;
    static final AtomicReference<String> lastRequest = new AtomicReference<>();
    static volatile int status = 200;
    static volatile byte[] reply = "%PDF-1.7\n%skonwertowany\n".getBytes(StandardCharsets.ISO_8859_1);

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forms/libreoffice/convert", ex -> {
            try (InputStream in = ex.getRequestBody()) {
                lastRequest.set(ex.getRequestHeaders().getFirst("Content-Type") + "\n" + new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
            }
            ex.sendResponseHeaders(status, reply.length);
            ex.getResponseBody().write(reply);
            ex.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static GotenbergConverter converter() {
        return new GotenbergConverter("http://127.0.0.1:" + server.getAddress().getPort() + "/", Duration.ofSeconds(5));
    }

    @Test
    void wysylaPlikZPolemPdfaIOdbieraPdf() throws IOException {
        status = 200;
        byte[] pdf = converter().toPdf(OfficeFormatTest.minimalDocx("Treść"), "skarga.docx");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
        String req = lastRequest.get();
        assertThat(req).startsWith("multipart/form-data; boundary=");
        assertThat(req).contains("name=\"pdfa\"").contains("PDF/A-2b").contains("name=\"files\"; filename=\"skarga.docx\"").contains("word/document.xml");
    }

    @Test
    void bladKonwerteraToStanKonfiguracji() {
        status = 500;
        assertThatThrownBy(() -> converter().toPdf(new byte[] {1, 2, 3}, "x.doc")).isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 500");
        status = 200;
        assertThatThrownBy(() -> converter().toPdf(new byte[0], "x.doc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GotenbergConverter("http://127.0.0.1:1", Duration.ofSeconds(2)).toPdf(new byte[] {1}, "x.docx"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("niedostępny");
        assertThat(GotenbergConverter.safeName("pismo ż (1).docx")).isEqualTo("pismo____1_.docx");
        assertThatThrownBy(() -> DocumentConverter.none().toPdf(new byte[] {1}, "x.docx")).hasMessageContaining("ESKARGA_DOCS_CONVERTERURL");
    }
}
