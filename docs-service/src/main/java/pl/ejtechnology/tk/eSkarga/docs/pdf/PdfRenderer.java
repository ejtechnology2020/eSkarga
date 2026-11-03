package pl.ejtechnology.tk.eSkarga.docs.pdf;

/**
 * Renderer PDF/A-2b za interfejsem (D13): pozwala wymienić OpenHTMLtoPDF bez zmian w domenie.
 * Każdy wynik przechodzi walidację veraPDF przed zapisem w repozytorium (T-026).
 */
public interface PdfRenderer {

    /** XHTML (z szablonu) → bajty PDF/A-2b z osadzonymi czcionkami z polskimi znakami. */
    byte[] renderPdfA(String xhtml, String baseUri);
}
