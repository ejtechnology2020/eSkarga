package pl.ejtechnology.tk.eSkarga.docs.convert;

import java.nio.charset.StandardCharsets;

/**
 * Rozpoznanie dokumentów Word po sygnaturze pliku (nie po rozszerzeniu): DOCX = archiwum ZIP z wpisem {@code word/},
 * DOC = kontener OLE2 ({@code D0 CF 11 E0 A1 B1 1A E1}). Ta sama reguła jest w aplikacji ({@code FileTypes}) przy rejestracji wpływu.
 */
public final class OfficeFormat {

    public static final String DOCX = "docx";
    public static final String DOC = "doc";
    public static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String DOC_TYPE = "application/msword";

    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private OfficeFormat() {
    }

    /** {@code docx}, {@code doc} albo {@code null}. */
    public static String detect(byte[] content) {
        if (content == null || content.length < 8) {
            return null;
        }
        if (content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4) {
            // nazwy wpisów ZIP są w nagłówkach lokalnych i katalogu centralnym – wystarczy szukać „word/” w treści
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

    public static String contentType(String format) {
        return DOCX.equals(format) ? DOCX_TYPE : DOC.equals(format) ? DOC_TYPE : null;
    }
}
