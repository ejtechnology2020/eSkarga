package pl.ejtechnology.tk.eSkarga.docs.api;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateRenderingException;

/**
 * Błędy → JSON bez szczegółów technicznych; błąd szablonu zwraca komunikat kompilatora (dla edytora S-24), stan (np. brak czcionek,
 * brak magazynu zaufania) – komunikat konfiguracyjny. Każdy błąd 500 (także {@link Error}, np. OutOfMemoryError) trafia do logu
 * z kodem {@code DOC-500-…}, który wraca do aplikacji w komunikacie – bez niego administrator nie znajdzie przyczyny.
 */
@Provider
public class DocsErrorMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger("pl.ejtechnology.tk.eSkarga.docs.blad");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    @Override
    public Response toResponse(Throwable e) {
        if (e instanceof WebApplicationException w) {
            return w.getResponse();
        }
        Response.Status status = e instanceof IllegalArgumentException || e instanceof TemplateRenderingException ? Response.Status.BAD_REQUEST
                : e instanceof IllegalStateException ? Response.Status.CONFLICT : Response.Status.INTERNAL_SERVER_ERROR;
        String msg;
        if (status == Response.Status.INTERNAL_SERVER_ERROR) {
            String code = "DOC-500-" + STAMP.format(ZonedDateTime.now(ZoneId.of("Europe/Warsaw"))) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            LOG.log(Level.SEVERE, "Błąd usługi dokumentów " + code + ": " + e, e);
            msg = "Błąd usługi dokumentów (kod " + code + " w logu serwera)";
        } else {
            msg = String.valueOf(e.getMessage());
            LOG.log(Level.WARNING, "Usługa dokumentów odrzuciła żądanie (" + status.getStatusCode() + "): " + msg);
        }
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Json.createObjectBuilder().add("komunikat", msg).build()).build();
    }
}
