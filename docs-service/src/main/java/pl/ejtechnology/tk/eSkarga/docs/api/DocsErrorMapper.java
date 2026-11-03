package pl.ejtechnology.tk.eSkarga.docs.api;

import jakarta.json.Json;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateRenderingException;

/** Błędy → JSON bez szczegółów technicznych; błąd szablonu zwraca komunikat kompilatora (dla edytora S-24). */
@Provider
public class DocsErrorMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException e) {
        if (e instanceof WebApplicationException w) {
            return w.getResponse();
        }
        Response.Status status = e instanceof IllegalArgumentException || e instanceof TemplateRenderingException ? Response.Status.BAD_REQUEST
                : e instanceof IllegalStateException ? Response.Status.CONFLICT : Response.Status.INTERNAL_SERVER_ERROR;
        String msg = status == Response.Status.INTERNAL_SERVER_ERROR ? "Błąd usługi dokumentów" : String.valueOf(e.getMessage());
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Json.createObjectBuilder().add("komunikat", msg).build()).build();
    }
}
