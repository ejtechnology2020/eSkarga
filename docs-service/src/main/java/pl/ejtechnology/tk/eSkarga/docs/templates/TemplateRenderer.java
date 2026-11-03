package pl.ejtechnology.tk.eSkarga.docs.templates;

import java.util.Map;

/**
 * Renderer szablonów pism (D13): szablon XHTML + płaski model danych → XHTML do konwersji na PDF/A.
 * Model to proste mapy i DTO; daty są już sformatowane po polsku („3 listopada 2026 r.”).
 */
public interface TemplateRenderer {

    /**
     * @param templateName nazwa i wersja szablonu (identyfikuje wersję zapisaną w bazie)
     * @param source       źródło szablonu (XHTML z FreeMarker)
     * @param model        płaski model danych pisma
     */
    String render(String templateName, String source, Map<String, Object> model);
}
