package pl.ejtechnology.tk.eSkarga.api;

import jakarta.json.Json;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Błędy domeny → kody HTTP bez szczegółów technicznych (NFR-10). */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException e) {
        Response.Status status;
        if (e instanceof IllegalArgumentException) {
            status = Response.Status.BAD_REQUEST;
        } else if (e instanceof IllegalStateException) {
            status = Response.Status.CONFLICT;
        } else if (e instanceof SecurityException) {
            status = Response.Status.FORBIDDEN;
        } else if (e instanceof jakarta.ws.rs.WebApplicationException w) {
            return w.getResponse();
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR;
        }
        String message = status == Response.Status.INTERNAL_SERVER_ERROR ? "Błąd wewnętrzny" : String.valueOf(e.getMessage());
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Json.createObjectBuilder().add("komunikat", message).build()).build();
    }
}
