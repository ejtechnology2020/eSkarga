package pl.ejtechnology.tk.eSkarga.documents;

import java.util.List;
import java.util.Map;

/**
 * Brama do usługi dokumentów (D13, T-026, T-031, T-040). Aplikacja nie renderuje PDF ani nie waliduje podpisów sama;
 * interfejs pozwala podstawić implementację testową bez HTTP.
 */
public interface DocsGateway {

    /** Wynik walidacji szablonu w edytorze (S-24). */
    record TemplateCheck(boolean ok, List<String> errors) {
    }

    /** Krok 1 podpisu dwuetapowego (D10). */
    record DataToSign(String sessionId, String digestBase64, String digestAlgorithm, String signatureAlgorithm) {
    }

    /** Wynik przetworzenia dokumentu (OCR, PDF/A, podpisy). */
    record Processed(byte[] pdf, String sha256, boolean pdfaCompliant, boolean ocrDone, String signatureReportJson, boolean anySignature, boolean allSignaturesValid) {
    }

    /** Pismo z szablonu (kod + źródło wersji z bazy) i płaskiego modelu → PDF/A-2b po veraPDF. */
    byte[] renderLetter(String templateCode, String templateSource, Map<String, Object> model);

    TemplateCheck validateTemplate(String templateCode, String templateSource);

    byte[] previewTemplate(String templateCode, String templateSource);

    DataToSign dataToSign(byte[] pdf, List<String> certificateChainBase64, String signatureAlgorithm, String reason);

    byte[] signDocument(String sessionId, String signatureValueBase64);

    Processed process(byte[] pdf, boolean ocr);

    byte[] watermark(byte[] pdf, String text);

    /** Domyślne szablony z usługi: kod → źródło (do zasiania tabeli {@code letter_template}). */
    Map<String, String> defaultTemplates();
}
