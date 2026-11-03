package pl.ejtechnology.tk.eSkarga.docs.api;

import java.io.IOException;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import pl.ejtechnology.tk.eSkarga.common.security.JwtRs256Verifier;
import pl.ejtechnology.tk.eSkarga.common.security.Settings;
import pl.ejtechnology.tk.eSkarga.common.security.TokenVerifier;

/**
 * Każde wywołanie usługi dokumentów wymaga tokenu ID-users (client credentials aplikacji albo workera) z rolą
 * {@value #ROLE}. W trybie {@code eskarga.security.mode=demo} na stacji deweloperskiej filtr przepuszcza żądania z sieci lokalnej.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class InternalTokenFilter implements ContainerRequestFilter {

    public static final String ROLE = "eskarga-docs";

    private volatile TokenVerifier verifier;

    TokenVerifier verifier() {
        if (verifier == null) {
            verifier = JwtRs256Verifier.fromSettings();
        }
        return verifier;
    }

    void override(TokenVerifier v) {
        this.verifier = v;
    }

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if ("demo".equalsIgnoreCase(Settings.value("eskarga.security.mode", "oidc"))) {
            return;
        }
        String auth = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build());
            return;
        }
        Optional<TokenVerifier.Claims> claims = verifier().verify(auth.substring(7).trim());
        if (claims.isEmpty()) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        } else if (!claims.get().roles().contains(ROLE)) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN).build());
        }
    }
}
