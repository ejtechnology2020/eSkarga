package pl.ejtechnology.tk.eSkarga.docs.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Testy piaskownicy FreeMarker (D13, T-026): ?new, ?api i wywołania klas Java kończą się błędem; polskie znaki przechodzą. */
class FreemarkerSandboxTest {

    private final FreemarkerTemplateRenderer renderer = new FreemarkerTemplateRenderer(2_000, 10_000);

    @Test
    void rendersOrderTemplateWithPolishDiacritics() throws Exception {
        String source = new String(getClass().getResourceAsStream("/templates/zarzadzenie-o-usunieciu-brakow.xhtml.ftl").readAllBytes(), StandardCharsets.UTF_8);
        String out = renderer.render("zarzadzenie", source, Map.of(
                "sygnatura", "Ts 312/26", "dataZarzadzenia", "28 października 2026", "sedzia", "Piotr Fikcyjny",
                "adresat", "adw. Annę Testową", "skarzacy", "Jana Przykładowego",
                "braki", List.of("pełnomocnictwo szczególne do sporządzenia skargi konstytucyjnej (art. 53 ust. 2 pkt 3)")));
        assertThat(out).contains("ZARZĄDZENIE").contains("Ts 312/26").contains("pełnomocnictwo szczególne").contains("Piotr Fikcyjny");
    }

    @Test
    void escapesHtmlInModelValues() {
        String out = renderer.render("t", "<p>${skarzacy}</p>", Map.of("skarzacy", "<script>alert(1)</script> & Spółka"));
        assertThat(out).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp;");
    }

    @Test
    void rejectsNewBuiltin() {
        assertThatThrownBy(() -> renderer.render("t", "${\"java.lang.ProcessBuilder\"?new()}", Map.of()))
                .isInstanceOf(TemplateRenderingException.class);
    }

    @Test
    void rejectsApiBuiltin() {
        assertThatThrownBy(() -> renderer.render("t", "${x?api.getClass()}", Map.of("x", "a")))
                .isInstanceOf(TemplateRenderingException.class);
    }

    @Test
    void rejectsObjectConstructorAccess() {
        assertThatThrownBy(() -> renderer.render("t", "${x.class.forName(\"java.lang.Runtime\")}", Map.of("x", "a")))
                .isInstanceOf(TemplateRenderingException.class);
    }

    @Test
    void enforcesOutputSizeLimit() {
        assertThatThrownBy(() -> renderer.render("t", "<#list 1..100000 as i>xxxxxxxxxx</#list>", Map.of()))
                .isInstanceOf(TemplateRenderingException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void enforcesTimeout() {
        FreemarkerTemplateRenderer slow = new FreemarkerTemplateRenderer(200, 100_000_000);
        assertThatThrownBy(() -> slow.render("t", "<#list 1..100000000 as i><#if i % 100000000 == 1>x</#if></#list>", Map.of()))
                .isInstanceOf(TemplateRenderingException.class)
                .hasMessageContaining("limit czasu");
    }
}
