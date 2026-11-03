package pl.ejtechnology.tk.eSkarga.docs.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import pl.ejtechnology.tk.eSkarga.docs.pdf.PdfAValidator;
import pl.ejtechnology.tk.eSkarga.docs.templates.SampleLetterModel;
import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateCatalog;
import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateRenderingException;

/**
 * Pisma z szablonów (T-026, T-026a): render do PDF/A-2b z walidacją veraPDF, walidacja szablonu na danych fikcyjnych,
 * domyślne szablony do zasiania tabeli {@code letter_template}.
 */
@Path("/pisma")
public class LettersResource {

    @Inject
    DocsServices services;

    /** {@code {"kod": "...", "zrodlo": "...", "model": {...}}} → PDF/A; 422 gdy wynik nie przechodzi veraPDF. */
    @POST
    @Path("/render")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/pdf")
    public Response render(JsonObject body) {
        try {
            return Response.ok(renderPdf(body)).header("Cache-Control", "no-store").build();
        } catch (PdfANonCompliantException e) {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            e.violations().forEach(arr::add);
            return Response.status(422).type(MediaType.APPLICATION_JSON)
                    .entity(Json.createObjectBuilder().add("komunikat", "Pismo nie spełnia PDF/A-2b").add("naruszenia", arr).build()).build();
        }
    }

    /** Wynik niezgodny z PDF/A-2b nigdy nie trafia do akt (D13). */
    public static final class PdfANonCompliantException extends RuntimeException {
        private final List<String> violations;

        PdfANonCompliantException(List<String> violations) {
            super("Pismo nie spełnia PDF/A-2b: " + violations);
            this.violations = violations;
        }

        public List<String> violations() {
            return violations;
        }
    }

    byte[] renderPdf(JsonObject body) {
        String code = body.getString("kod");
        String source = body.getString("zrodlo");
        Map<String, Object> model = toModel(body.getJsonObject("model"));
        String xhtml = services.templates().render(code, source, model);
        byte[] pdf = services.pdf().renderPdfA(xhtml, null);
        PdfAValidator.Result v = services.pdfa().validate(pdf);
        if (!v.compliant()) {
            throw new PdfANonCompliantException(v.violations());
        }
        return pdf;
    }

    /** Walidacja szablonu przed aktywacją: kompilacja + render na Ts 312/26 + PDF + veraPDF → {@code {"ok": bool, "bledy": [...]}}. */
    @POST
    @Path("/szablony/waliduj")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject validate(JsonObject body) {
        String code = body.getString("kod", "SZABLON");
        String source = body.getString("zrodlo");
        JsonArrayBuilder errors = Json.createArrayBuilder();
        try {
            String xhtml = services.templates().render(code, source, SampleLetterModel.ts312());
            byte[] pdf = services.pdf().renderPdfA(xhtml, null);
            PdfAValidator.Result v = services.pdfa().validate(pdf);
            if (!v.compliant()) {
                v.violations().forEach(x -> errors.add("PDF/A: " + x));
            }
        } catch (TemplateRenderingException | IllegalStateException e) {
            errors.add(e.getMessage());
        }
        var arr = errors.build();
        return Json.createObjectBuilder().add("ok", arr.isEmpty()).add("bledy", arr).build();
    }

    /** Podgląd szablonu w edytorze na danych fikcyjnych. */
    @POST
    @Path("/szablony/podglad")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/pdf")
    public Response preview(JsonObject body) {
        String xhtml = services.templates().render(body.getString("kod", "PODGLAD"), body.getString("zrodlo"), SampleLetterModel.ts312());
        return Response.ok(services.pdf().renderPdfA(xhtml, null)).header("Cache-Control", "no-store").build();
    }

    @GET
    @Path("/szablony/domyslne")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject defaults() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (String code : TemplateCatalog.codes()) {
            arr.add(Json.createObjectBuilder().add("kod", code).add("plik", TemplateCatalog.FILES.get(code)).add("zrodlo", TemplateCatalog.source(code)));
        }
        return Json.createObjectBuilder().add("szablony", arr).build();
    }

    /** JSON → płaski model (mapy, listy, teksty, liczby, flagi) – nigdy obiekty Java (D13). */
    static Map<String, Object> toModel(JsonObject json) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (json == null) {
            return m;
        }
        json.forEach((k, v) -> m.put(k, toValue(v)));
        return m;
    }

    private static Object toValue(JsonValue v) {
        return switch (v.getValueType()) {
            case STRING -> ((JsonString) v).getString();
            case NUMBER -> ((jakarta.json.JsonNumber) v).isIntegral() ? (Object) ((jakarta.json.JsonNumber) v).longValue() : (Object) ((jakarta.json.JsonNumber) v).doubleValue();
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case ARRAY -> ((jakarta.json.JsonArray) v).stream().map(LettersResource::toValue).toList();
            case OBJECT -> toModel((JsonObject) v);
            default -> null;
        };
    }

    static List<String> strings(JsonObject o, String key) {
        return o.containsKey(key) ? o.getJsonArray(key).getValuesAs(JsonString.class).stream().map(JsonString::getString).toList() : List.of();
    }
}
