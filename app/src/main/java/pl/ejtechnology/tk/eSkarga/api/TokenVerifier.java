package pl.ejtechnology.tk.eSkarga.api;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Weryfikacja tokenu dostępu workera. Implementacja produkcyjna: {@link JwtRs256Verifier} z JWKS ID-users. */
public interface TokenVerifier {

    /** Potwierdzone roszczenia tokenu. */
    record Claims(String subject, String issuer, Set<String> roles, Instant expiresAt) {
    }

    Optional<Claims> verify(String token);
}
