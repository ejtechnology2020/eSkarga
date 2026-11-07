package pl.ejtechnology.tk.eSkarga.documents;

import java.nio.charset.StandardCharsets;

/**
 * Rozpoznanie typu pliku po sygnaturze (nie po rozszerzeniu, T-055): PDF, TIFF oraz dokumenty Word (ADR 0026) – DOCX jako ZIP
 * z wpisem {@code word/}, DOC jako kontener OLE2. Dokumenty Word trafiają do akt po konwersji do PDF/A w usłudze dokumentów;
 * do tego czasu plik w repozytorium jest oryginałem.
 */
public final class FileTypes {

    public static final String PDF = "application/pdf";
    public static final String TIFF = "image/tiff";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String DOC = "application/msword";

    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private FileTypes() {
    }

    /** Typ MIME po sygnaturze albo {@code null}, gdy plik nie jest obsługiwany. */
    public static String detect(byte[] content) {
        if (content == null || content.length < 8) {
            return null;
        }
        if (content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F' && content[4] == '-') {
            return PDF;
        }
        if ((content[0] == 'I' && content[1] == 'I' && content[2] == 42 && content[3] == 0) || (content[0] == 'M' && content[1] == 'M' && content[2] == 0 && content[3] == 42)) {
            return TIFF;
        }
        if (content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4) {
            String head = new String(content, 0, Math.min(content.length, 4 * 1024 * 1024), StandardCharsets.ISO_8859_1);
            return head.contains("word/document.xml") || head.contains("word/_rels/") ? DOCX : null;
        }
        for (int i = 0; i < OLE.length; i++) {
            if (content[i] != OLE[i]) {
                return null;
            }
        }
        return DOC;
    }

    /** Dokument Word – do akt po konwersji (ADR 0026). */
    public static boolean isOffice(String contentType) {
        return DOCX.equals(contentType) || DOC.equals(contentType);
    }

    /** Plik, który może stać się kartą akt: PDF od razu, DOC/DOCX po konwersji; TIFF zostaje tylko skanem wpływu. */
    public static boolean isCaseDocument(String contentType) {
        return PDF.equals(contentType) || isOffice(contentType);
    }

    public static String extension(String contentType) {
        return DOCX.equals(contentType) ? "docx" : DOC.equals(contentType) ? "doc" : TIFF.equals(contentType) ? "tif" : "pdf";
    }

    public static String label(String contentType) {
        return DOCX.equals(contentType) ? "DOCX" : DOC.equals(contentType) ? "DOC" : TIFF.equals(contentType) ? "TIFF" : "PDF";
    }
}
