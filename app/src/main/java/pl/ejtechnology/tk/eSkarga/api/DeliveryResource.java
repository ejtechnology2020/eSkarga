package pl.ejtechnology.tk.eSkarga.api;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import pl.ejtechnology.tk.eSkarga.domain.DeliveryMethod;
import pl.ejtechnology.tk.eSkarga.domain.model.Delivery;
import pl.ejtechnology.tk.eSkarga.domain.service.DeliveryService;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;

/**
 * Doręczenia dla workera (T-041, T-043): lista pism do wysyłki, treść dokumentu, raport statusu. Statusy operatora
 * (e-Doręczenia, Poczta) są mapowane na {@link DeliveryMethod} i uruchamiają termin (D6) przez {@link DeliveryService}.
 */
@Path("/doreczenia")
@Produces(MediaType.APPLICATION_JSON)
public class DeliveryResource {

    @Inject
    DeliveryService deliveries;
    @Inject
    DocumentStore store;

    @GET
    @Path("/do-wysylki")
    public JsonObject toSend(@QueryParam("sygnatura") String sygnatura) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        List<Delivery> list = deliveries.toSend();
        for (Delivery d : list) {
            if (sygnatura != null && !sygnatura.isBlank() && !d.getDocument().getCaseFile().getDocket().format().equals(sygnatura)) {
                continue;
            }
            arr.add(toJson(d));
        }
        return Json.createObjectBuilder().add("doreczenia", arr).build();
    }

    @GET
    @Path("/{id}")
    public JsonObject one(@PathParam("id") long id) {
        return toJson(deliveries.require(id));
    }

    /** Treść pisma do wysyłki (strumień z repozytorium, bez zapisu na dysku workera). */
    @GET
    @Path("/{id}/dokument")
    @Produces("application/pdf")
    public Response document(@PathParam("id") long id) {
        Delivery d = deliveries.require(id);
        String key = d.getDocument().getStorageKey();
        if (key == null) {
            throw new IllegalStateException("Dokument nie ma pliku w repozytorium");
        }
        InputStream in = store.get(key, Optional.empty());
        return Response.ok(in).header("Cache-Control", "no-store").header("X-Document-Sha256", d.getDocument().getSha256()).build();
    }

    /**
     * Status od operatora: {@code WYSLANE} (numer nadania, data), {@code DORECZONE} (data, sposób: DO_RAK / ZASTEPCZE),
     * {@code AWIZO} (data pierwszego awizo), {@code ZWROT} (pismo wróciło – bez skutku doręczenia).
     */
    @POST
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response status(@PathParam("id") long id, JsonObject body, @Context SecurityContext sc) {
        String status = body.getString("status");
        LocalDate date = LocalDate.parse(body.getString("data"));
        String actor = "worker:" + (sc.getUserPrincipal() == null ? "?" : sc.getUserPrincipal().getName());
        switch (status) {
            case "WYSLANE" -> deliveries.confirmDispatch(id, body.getString("numerNadania", null), date, actor);
            case "DORECZONE" -> deliveries.registerReceipt(id, date, DeliveryMethod.valueOf(body.getString("sposob", "DO_RAK")), actor);
            case "AWIZO" -> deliveries.registerReceipt(id, date, DeliveryMethod.AWIZO, actor);
            case "ZWROT" -> deliveries.returned(id, date, body.getString("powod", ""), actor);
            default -> throw new IllegalArgumentException("Nieznany status doręczenia: " + status);
        }
        return Response.noContent().build();
    }

    static JsonObject toJson(Delivery d) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("id", d.getId())
                .add("kluczIdempotencji", d.getIdempotencyKey())
                .add("kanal", d.getChannel().name())
                .add("sygnatura", d.getDocument().getCaseFile().getDocket().format())
                .add("tytul", d.getDocument().getTitle())
                .add("adresat", d.getAddressee().getDisplayName());
        if (d.getAddressee().getDeliveryAddress() != null) {
            b.add("adres", d.getAddressee().getDeliveryAddress());
        }
        if (d.getAddressee().getEDeliveryAddress() != null) {
            b.add("adresEDoreczen", d.getAddressee().getEDeliveryAddress());
        }
        if (d.getSentOn() != null) {
            b.add("dataNadania", d.getSentOn().toString());
        }
        if (d.getDispatchNo() != null) {
            b.add("numerNadania", d.getDispatchNo());
        }
        return b.build();
    }
}
