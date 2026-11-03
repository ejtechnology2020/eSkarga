package pl.ejtechnology.tk.eSkarga.api;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;

/** Dane sprawy do przekazania do repertorium SK/K/U (UC-12) dla workera {@code przekazanie-merytoryczne}. */
@Path("/sprawy")
@Produces(MediaType.APPLICATION_JSON)
public class TransferResource {

    @Inject
    CaseFileService cases;

    @GET
    @Path("/{sygnatura}/przekazanie")
    public JsonObject transferData(@PathParam("sygnatura") String sygnatura) {
        CaseFile cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElseThrow(() -> new NotFoundException("Brak sprawy " + sygnatura));
        JsonArrayBuilder parties = Json.createArrayBuilder();
        for (Party p : cases.parties(cf)) {
            parties.add(Json.createObjectBuilder().add("rola", p.getRole().name()).add("nazwa", p.getDisplayName()));
        }
        return Json.createObjectBuilder()
                .add("sygnatura", cf.getDocket().format())
                .add("rodzaj", cf.getDocket().symbol().name())
                .add("dataWpisu", cf.getEnteredOn().toString())
                .add("przedmiot", String.valueOf(cf.current().getReviewSubject()))
                .add("wzorzec", String.valueOf(cf.current().getReviewStandard()))
                .add("strony", parties)
                .add("kluczIdempotencji", "PRZEKAZ-" + cf.getDocket().format().replace(' ', '-').replace('/', '-'))
                .build();
    }
}
