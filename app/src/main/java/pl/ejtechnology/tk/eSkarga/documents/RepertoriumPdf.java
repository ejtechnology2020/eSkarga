package pl.ejtechnology.tk.eSkarga.documents;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;

/**
 * Roczny wyciąg z repertorium Ts/Tw do PDF (S-08, UC-20): tabela wpisów jak na ekranie, w kolejności sygnatur, z nagłówkiem
 * (repertorium, rok, data wydruku, liczba wpisów). Silnik OpenHTMLtoPDF jak w usłudze dokumentów (ADR 0001); czcionka Liberation
 * Serif z {@code eskarga.pdf.fontDir} / {@code ESKARGA_PDF_FONTDIR} (dev: /usr/share/fonts/truetype/liberation) osadzana w trybie PDF/A-2b;
 * bez czcionek – zwykły PDF z czcionkami wbudowanymi (bez gwarancji polskich znaków), żeby eksport działał także na stacji bez pakietu fontów.
 */
public final class RepertoriumPdf {

    private final File fontDir;
    private final Function<String, String> statusLabel;

    public RepertoriumPdf(File fontDir, Function<String, String> statusLabel) {
        this.fontDir = fontDir;
        this.statusLabel = statusLabel;
    }

    public static File defaultFontDir() {
        String v = System.getProperty("eskarga.pdf.fontDir");
        if (v == null || v.isBlank()) {
            v = System.getenv("ESKARGA_PDF_FONTDIR");
        }
        return new File(v == null || v.isBlank() ? "/usr/share/fonts/truetype/liberation" : v);
    }

    public byte[] render(DocketSymbol symbol, int year, List<CaseSummary> rows, LocalDate printedOn, String printedBy) {
        String html = html(symbol, year, rows, printedOn, printedBy);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            File regular = new File(fontDir, "LiberationSerif-Regular.ttf");
            File bold = new File(fontDir, "LiberationSerif-Bold.ttf");
            if (regular.isFile()) {
                builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B);
                builder.useColorProfile(java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData());
                builder.useFont(regular, "Liberation Serif", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                builder.useFont(regular, "serif", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                if (bold.isFile()) {
                    builder.useFont(bold, "Liberation Serif", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
                    builder.useFont(bold, "serif", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
                }
            }
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.createPDF();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się wygenerować wyciągu z repertorium", e);
        }
    }

    String html(DocketSymbol symbol, int year, List<CaseSummary> rows, LocalDate printedOn, String printedBy) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\"/><title>Repertorium ").append(symbol.code()).append(' ').append(year)
          .append("</title><style>")
          .append("@page{size:A4 landscape;margin:14mm 12mm;@bottom-right{content:'Strona ' counter(page) ' z ' counter(pages);font-family:'Liberation Serif',serif;font-size:9pt}}")
          .append("body{font-family:'Liberation Serif',serif;font-size:9.5pt;color:#111}h1{font-size:15pt;margin:0 0 2mm}p.meta{margin:0 0 5mm;font-size:9pt}")
          .append("table{width:100%;border-collapse:collapse}th,td{border:0.3pt solid #777;padding:1.6mm 2mm;text-align:left;vertical-align:top}")
          .append("th{background:#e8ebf0;font-weight:bold}td.n{white-space:nowrap}tfoot td{border:0;padding-top:4mm;font-size:8.5pt}")
          .append("</style></head><body>")
          .append("<h1>Repertorium ").append(e(symbol.code())).append(" · ").append(year).append("</h1>")
          .append("<p class=\"meta\">Wyciąg roczny · wydruk ").append(e(Fmt.DATE.format(printedOn))).append(" · ").append(e(printedBy))
          .append(" · wpisów: ").append(rows.size()).append(". Wpisów nie usuwa się; korekta ma historię wersji (NFR-01).</p>")
          .append("<table><thead><tr><th>Sygnatura</th><th>Data wpisu</th><th>Skarżący / wnioskodawca</th><th>Pełnomocnik</th><th>Przedmiot</th>")
          .append("<th>Sędzia</th><th>Status</th><th>Sposób zakończenia</th><th>Data zakończenia</th><th>Sygn. merytoryczna</th><th>Wersja</th></tr></thead><tbody>");
        for (CaseSummary c : rows) {
            sb.append("<tr><td class=\"n\">").append(e(c.sygnatura().format())).append("</td>")
              .append("<td class=\"n\">").append(c.dataWpisu() == null ? "" : e(Fmt.DATE.format(c.dataWpisu()))).append("</td>")
              .append("<td>").append(e(c.skarzacy())).append("</td>")
              .append("<td>").append(e(c.pelnomocnik())).append("</td>")
              .append("<td>").append(e(c.przedmiot())).append("</td>")
              .append("<td>").append(e(c.sedzia() == null ? "oczekuje na wyznaczenie" : c.sedzia())).append("</td>")
              .append("<td>").append(e(statusLabel.apply(c.status().name()))).append("</td>")
              .append("<td>").append(e(c.sposobZakonczenia())).append("</td>")
              .append("<td class=\"n\">").append(c.dataZakonczenia() == null ? "" : e(Fmt.DATE.format(c.dataZakonczenia()))).append("</td>")
              .append("<td class=\"n\">").append(c.powiazana() == null ? "" : e(c.powiazana().format())).append("</td>")
              .append("<td class=\"n\">").append(e(c.wersja())).append("</td></tr>");
        }
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan=\"11\">Brak wpisów w tym roku.</td></tr>");
        }
        sb.append("</tbody><tfoot><tr><td colspan=\"11\">eSkarga – Repertorium Ts/Tw. Dokument wygenerowany z systemu; wiążąca jest treść repertorium w systemie.</td></tr></tfoot></table>")
          .append("</body></html>");
        return sb.toString();
    }

    private static String e(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }
}
