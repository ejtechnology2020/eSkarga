package pl.ejtechnology.tk.eSkarga.docs.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import pl.ejtechnology.tk.eSkarga.docs.crypto.PubSecDecryptor;
import pl.ejtechnology.tk.eSkarga.docs.ocr.OcrService;
import pl.ejtechnology.tk.eSkarga.docs.pdf.PdfAValidator;
import pl.ejtechnology.tk.eSkarga.docs.redaction.Anonymizer;
import pl.ejtechnology.tk.eSkarga.docs.redaction.PdfRedactor;
import pl.ejtechnology.tk.eSkarga.docs.signature.SignatureReport;

/**
 * Przetwarzanie dokumentów akt (T-031, T-036, T-037): odszyfrowanie PubSec, walidacja podpisów, OCR, PDF/A, znak wodny,
 * anonimizacja. Wołane przez workera {@code dokument-przetworz} z ponawianiem; wynik idempotentny dla tych samych bajtów.
 */
@Path("/dokumenty")
public class DocumentsResource {

    @Inject
    DocsServices services;

    /** Wejście: PDF; wyjście: metadane + dokument po przetworzeniu (base64). */
    @POST
    @Path("/przetworz")
    @Consumes("application/pdf")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject process(byte[] pdf, @QueryParam("ocr") @DefaultValue("true") boolean ocr) {
        byte[] current = pdf;
        boolean decrypted = false;
        if (PubSecDecryptor.isEncrypted(current) && services.hsm() != null) {
            current = new PubSecDecryptor(services.hsm(), "pubsec", new char[0]).decrypt(current);
            decrypted = true;
        }
        SignatureReport signatures = services.signatures().validate(current);
        OcrService.Result ocrResult = ocr && !signatures.anySignature() ? services.ocr().ocr(current) : new OcrService.Result(current, false,
                signatures.anySignature() ? "OCR pominięty: dokument podpisany – zachowano oryginał" : "OCR wyłączony");
        current = ocrResult.pdf();
        PdfAValidator.Result pdfa = services.pdfa().validate(current);
        JsonArrayBuilder violations = Json.createArrayBuilder();
        pdfa.violations().stream().limit(20).forEach(violations::add);
        return Json.createObjectBuilder()
                .add("sha256", sha256(current))
                .add("rozmiar", current.length)
                .add("odszyfrowany", decrypted)
                .add("pdfaZgodny", pdfa.compliant())
                .add("naruszeniaPdfa", violations)
                .add("ocrWykonany", ocrResult.ocrDone())
                .add("uwagaOcr", ocrResult.note() == null ? "" : ocrResult.note())
                .add("raportPodpisu", signatures.toJson())
                .add("tekst", extractText(current))
                .add("pdf", Base64.getEncoder().encodeToString(current))
                .build();
    }

    @POST
    @Path("/podpisy")
    @Consumes("application/pdf")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject signatures(byte[] pdf) {
        return services.signatures().validate(pdf).toJson();
    }

    @POST
    @Path("/pdfa")
    @Consumes("application/pdf")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject pdfa(byte[] pdf) {
        PdfAValidator.Result r = services.pdfa().validate(pdf);
        JsonArrayBuilder arr = Json.createArrayBuilder();
        r.violations().forEach(arr::add);
        return Json.createObjectBuilder().add("zgodny", r.compliant()).add("naruszenia", arr).build();
    }

    @POST
    @Path("/znak-wodny")
    @Consumes("application/pdf")
    @Produces("application/pdf")
    public Response watermark(byte[] pdf, @QueryParam("tekst") String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Brak tekstu znaku wodnego");
        }
        return Response.ok(services.watermark().apply(pdf, text)).header("Cache-Control", "no-store").build();
    }

    /** {@code {"pdf": base64, "pola": [{"strona":1,"x":..,"y":..,"szerokosc":..,"wysokosc":..}], "nazwiska": [...]}} → zanonimizowany PDF (rastrowy). */
    @POST
    @Path("/anonimizuj")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/pdf")
    public Response anonymize(JsonObject body) {
        byte[] pdf = Base64.getDecoder().decode(body.getString("pdf"));
        List<PdfRedactor.Box> boxes = new ArrayList<>();
        if (body.containsKey("pola")) {
            for (JsonObject p : body.getJsonArray("pola").getValuesAs(JsonObject.class)) {
                boxes.add(new PdfRedactor.Box(p.getInt("strona"), (float) p.getJsonNumber("x").doubleValue(), (float) p.getJsonNumber("y").doubleValue(),
                        (float) p.getJsonNumber("szerokosc").doubleValue(), (float) p.getJsonNumber("wysokosc").doubleValue()));
            }
        }
        return Response.ok(services.redactor().redact(pdf, boxes)).header("Cache-Control", "no-store").build();
    }

    /** Anonimizacja tekstu (metadane, OCR do publikacji): {@code {"tekst": "...", "nazwiska": [...]}}. */
    @POST
    @Path("/anonimizuj-tekst")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject anonymizeText(JsonObject body) {
        Anonymizer.Result r = new Anonymizer(LettersResource.strings(body, "nazwiska")).anonymize(body.getString("tekst"));
        JsonArrayBuilder kinds = Json.createArrayBuilder();
        r.kinds().forEach(kinds::add);
        return Json.createObjectBuilder().add("tekst", r.text()).add("zamiany", r.replacements()).add("rodzaje", kinds).build();
    }

    /** Warstwa tekstowa do indeksu wyszukiwania (T-032); limit 200 000 znaków. */
    static String extractText(byte[] pdf) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            return text.length() > 200_000 ? text.substring(0, 200_000) : text;
        } catch (java.io.IOException | RuntimeException e) {
            return "";
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
