package pl.ejtechnology.tk.eSkarga.domain.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Eksport tabel do CSV i XLSX bez zewnętrznych bibliotek (T-050): XLSX jako minimalny pakiet OOXML z napisami inline.
 * Wystarcza dla raportów i rejestrów; złożone formatowanie nie jest potrzebne.
 */
public final class TabularExport {

    private TabularExport() {
    }

    public static byte[] csv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        appendCsvRow(sb, header);
        for (List<String> r : rows) {
            appendCsvRow(sb, r);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvRow(StringBuilder sb, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            String v = row.get(i) == null ? "" : row.get(i);
            boolean quote = v.contains(";") || v.contains("\"") || v.contains("\n");
            sb.append(quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
            if (i < row.size() - 1) {
                sb.append(';');
            }
        }
        sb.append("\r\n");
    }

    public static byte[] xlsx(String sheetName, List<String> header, List<List<String>> rows) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>""");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets></workbook>""".formatted(esc(sheetName)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>""");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            appendRow(sheet, 1, header);
            for (int i = 0; i < rows.size(); i++) {
                appendRow(sheet, i + 2, rows.get(i));
            }
            sheet.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void appendRow(StringBuilder sb, int rowNo, List<String> cells) {
        sb.append("<row r=\"").append(rowNo).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            String v = cells.get(c) == null ? "" : cells.get(c);
            sb.append("<c r=\"").append(col(c)).append(rowNo).append("\" t=\"inlineStr\"><is><t>").append(esc(v)).append("</t></is></c>");
        }
        sb.append("</row>");
    }

    static String col(int idx) {
        StringBuilder s = new StringBuilder();
        idx++;
        while (idx > 0) {
            int rem = (idx - 1) % 26;
            s.insert(0, (char) ('A' + rem));
            idx = (idx - 1) / 26;
        }
        return s.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
