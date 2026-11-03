package pl.ejtechnology.tk.eSkarga.api;

import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService;

/** Kolejka przetwarzania dokumentów dla workera {@code dokument-przetworz} (T-031): lista, plik, wynik. */
@Path("/dokumenty")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentsApiResource {

    @Inject
    CaseDocumentService documents;

    @GET
    @Path("/do-przetworzenia")
    public JsonObject pending() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (CaseDocument d : documents.pendingProcessing()) {
            arr.add(Json.createObjectBuilder().add("id", d.getId()).add("sygnatura", d.getCaseFile().getDocket().format()).add("tytul", d.getTitle()).add("sha256", d.getSha256()));
        }
        return Json.createObjectBuilder().add("dokumenty", arr).build();
    }

    @GET
    @Path("/{id}/plik")
    @Produces("application/pdf")
    public Response file(@PathParam("id") long id) {
        CaseDocument d = documents.require(id);
        InputStream in = documents.content(d, Optional.empty());
        return Response.ok(in).header("Cache-Control", "no-store").header("X-Document-Sha256", d.getSha256()).build();
    }

    /** Wynik z usługi dokumentów (ten sam JSON, który zwraca {@code /dokumenty/przetworz}). */
    @POST
    @Path("/{id}/wynik")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response result(@PathParam("id") long id, JsonObject body, @Context SecurityContext sc) {
        JsonObject report = body.getJsonObject("raportPodpisu");
        byte[] pdf = body.containsKey("pdf") ? Base64.getDecoder().decode(body.getString("pdf")) : null;
        documents.applyProcessing(id, new CaseDocumentService.ProcessingResult(pdf, body.getString("sha256", null), body.getBoolean("pdfaZgodny", false),
                body.getBoolean("ocrWykonany", false), report == null ? null : report.toString(), report != null && report.getBoolean("podpisany", false),
                report == null || report.getBoolean("wszystkieWazne", true)), "worker:" + (sc.getUserPrincipal() == null ? "?" : sc.getUserPrincipal().getName()));
        return Response.noContent().build();
    }
}
