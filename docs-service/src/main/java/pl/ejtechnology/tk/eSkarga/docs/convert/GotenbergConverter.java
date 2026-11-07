package pl.ejtechnology.tk.eSkarga.docs.convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Konwerter przez Gotenberg (LibreOffice za API HTTP, osobny kontener/usługa – ADR 0026): {@code POST /forms/libreoffice/convert}
 * z plikiem i polem {@code pdfa=PDF/A-2b}. Plik to dane niezaufane – idzie do odizolowanego procesu, z limitem rozmiaru i czasu;
 * wynik i tak przechodzi veraPDF w ścieżce przetwarzania.
 */
public final class GotenbergConverter implements DocumentConverter {

    public static final long MAX_BYTES = 50L * 1024 * 1024;

    private final String baseUrl;
    private final HttpClient http;
    private final Duration timeout;

    public GotenbergConverter(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public byte[] toPdf(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Pusty plik do konwersji");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("Plik do konwersji przekracza " + (MAX_BYTES / 1024 / 1024) + " MB");
        }
        String boundary = "----eskarga-" + UUID.randomUUID();
        byte[] body = multipart(boundary, content, safeName(fileName));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/forms/libreoffice/convert"))
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Konwerter dokumentów niedostępny (" + baseUrl + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwano konwersję dokumentu", e);
        }
        if (resp.statusCode() / 100 != 2) {
            String detail = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Konwerter dokumentów: HTTP " + resp.statusCode() + (detail.isBlank() ? "" : " " + detail.strip().replaceAll("\\s+", " ")
                    .substring(0, Math.min(200, detail.strip().length()))));
        }
        byte[] pdf = resp.body();
        if (pdf == null || pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw new IllegalStateException("Konwerter dokumentów zwrócił dane, które nie są plikiem PDF");
        }
        return pdf;
    }

    static String safeName(String fileName) {
        String n = fileName == null ? "dokument.docx" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.contains(".") ? n : n + ".docx";
    }

    static byte[] multipart(String boundary, byte[] file, String fileName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 512)) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"pdfa\"\r\n\r\nPDF/A-2b\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(file);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
