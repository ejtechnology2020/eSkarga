package pl.ejtechnology.tk.eSkarga.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pl.ejtechnology.tk.eSkarga.documents.DocsGateway;

/** Zaślepka usługi dokumentów: „PDF” to tekst z nazwą szablonu i modelem; podpis dopisuje znacznik. Zapamiętuje wywołania. */
public class StubDocsGateway implements DocsGateway {

    public final List<String> rendered = new ArrayList<>();
    public final List<String> signedSessions = new ArrayList<>();
    public boolean signaturesValid = true;

    @Override
    public byte[] renderLetter(String templateCode, String templateSource, Map<String, Object> model) {
        rendered.add(templateCode);
        return ("%PDF-1.7 /Type /Page " + templateCode + " " + model.get("sygnatura") + " " + model.get("skarzacy") + " " + model.get("braki") + " " + model.get("sedzia")).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public TemplateCheck validateTemplate(String templateCode, String templateSource) {
        return templateSource.contains("<script") ? new TemplateCheck(false, List.of("niedozwolona treść")) : new TemplateCheck(true, List.of());
    }

    @Override
    public byte[] previewTemplate(String templateCode, String templateSource) {
        return "%PDF-1.7 podglad".getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DataToSign dataToSign(byte[] pdf, List<String> certificateChainBase64, String signatureAlgorithm, String reason) {
        return new DataToSign("sesja-" + pdf.length, "c2tyb3Q=", "SHA-256", signatureAlgorithm);
    }

    @Override
    public byte[] signDocument(String sessionId, String signatureValueBase64) {
        signedSessions.add(sessionId);
        return ("%PDF-1.7 /Type /Page podpisany " + sessionId).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Processed process(byte[] pdf, boolean ocr) {
        boolean signed = new String(pdf, StandardCharsets.UTF_8).contains("podpisany");
        return new Processed(pdf, InMemoryDocumentStore.sha256(pdf), true, ocr, signed ? "{\"podpisany\":true}" : "{\"podpisany\":false}", signed, signaturesValid);
    }

    @Override
    public byte[] watermark(byte[] pdf, String text) {
        return (new String(pdf, StandardCharsets.UTF_8) + " " + text).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, String> defaultTemplates() {
        return Map.of("ZARZ_BRAKI", "<html>${sygnatura}</html>", "POST_ODMOWA", "<html>${sygnatura}</html>", "POST_NADANIE_BIEGU", "<html/>", "ODP_PISMO_OGOLNE", "<html/>",
                "ZARZ_WYZNACZENIE_SEDZIEGO", "<html>${sygnatura} ${sedzia}</html>");
    }
}
