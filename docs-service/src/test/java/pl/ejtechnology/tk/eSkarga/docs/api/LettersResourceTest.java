package pl.ejtechnology.tk.eSkarga.docs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateCatalog;

/** T-026a: walidacja szablonu przed aktywacją (błąd składni blokuje), render pisma, konwersja modelu JSON → płaski model. */
class LettersResourceTest {

    static LettersResource resource;

    @BeforeAll
    static void init() {
        DocsServices s = new DocsServices();
        s.init();
        resource = new LettersResource();
        resource.services = s;
    }

    private static JsonObject json(String s) {
        return Json.createReader(new StringReader(s)).readObject();
    }

    @Test
    void szablonZBledemSkladniNieJestPoprawny() {
        JsonObject r = resource.validate(json("{\"kod\":\"X\",\"zrodlo\":\"<p>${sygnatura</p>\"}"));
        assertThat(r.getBoolean("ok")).isFalse();
        assertThat(r.getJsonArray("bledy")).isNotEmpty();
        JsonObject api = resource.validate(json("{\"kod\":\"X\",\"zrodlo\":\"<p>${sygnatura?api.getClass()}</p>\"}"));
        assertThat(api.getBoolean("ok")).as("piaskownica D13").isFalse();
    }

    @Test
    void poprawnySzablonPrzechodziWalidacjeIRender() {
        assumeTrue(new File("/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf").isFile());
        String source = TemplateCatalog.source("ZARZ_BRAKI");
        JsonObject body = Json.createObjectBuilder().add("kod", "ZARZ_BRAKI").add("zrodlo", source).build();
        JsonObject r = resource.validate(body);
        assertThat(r.getJsonArray("bledy")).isEmpty();
        assertThat(r.getBoolean("ok")).isTrue();

        JsonObject render = Json.createObjectBuilder().add("kod", "ZARZ_BRAKI").add("zrodlo", source)
                .add("model", Json.createObjectBuilder().add("sygnatura", "Ts 312/26").add("dataZarzadzenia", "3 listopada 2026").add("sedzia", "Piotr Fikcyjny")
                        .add("adresat", "adw. Annę Testową").add("skarzacy", "Jana Przykładowego")
                        .add("braki", Json.createArrayBuilder().add("pełnomocnictwo szczególne"))).build();
        byte[] pdf = resource.renderPdf(render);
        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(resource.defaults().getJsonArray("szablony")).hasSize(TemplateCatalog.codes().size());
    }

    @Test
    void modelJsonToPlaskieMapyIListy() {
        Map<String, Object> m = LettersResource.toModel(json("{\"a\":\"x\",\"n\":3,\"f\":true,\"l\":[\"p\",\"q\"],\"o\":{\"k\":1.5}}"));
        assertThat(m).containsEntry("a", "x").containsEntry("n", 3L).containsEntry("f", true).containsEntry("l", List.of("p", "q"));
        assertThat(((Map<?, ?>) m.get("o")).get("k")).isEqualTo(1.5);
    }
}
