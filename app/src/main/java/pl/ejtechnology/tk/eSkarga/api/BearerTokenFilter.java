package pl.ejtechnology.tk.eSkarga.api;

import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/** Każde żądanie do {@code /api} musi mieć ważny token z rolą {@value #WORKER_ROLE}; odmowa trafia do dziennika zdarzeń. */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class BearerTokenFilter implements ContainerRequestFilter {

    public static final String WORKER_ROLE = "eskarga-worker";

    @Inject
    TokenVerifierProducer verifiers;
    @Inject
    AuditService audit;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String auth = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build());
            return;
        }
        Optional<TokenVerifier.Claims> claims = verifiers.verifier().verify(auth.substring("Bearer ".length()).trim());
        if (claims.isEmpty()) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build());
            return;
        }
        TokenVerifier.Claims c = claims.get();
        if (!c.roles().contains(WORKER_ROLE)) {
            audit.recordDenial(c.subject(), String.join(",", c.roles()), "odmowa: API workerów bez roli " + WORKER_ROLE, ctx.getUriInfo().getPath(), null, null);
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return;
        }
        ctx.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return c::subject;
            }

            @Override
            public boolean isUserInRole(String role) {
                return c.roles().contains(role);
            }

            @Override
            public boolean isSecure() {
                return true;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }
}
