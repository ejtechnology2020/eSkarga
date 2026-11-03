package pl.ejtechnology.tk.eSkarga.api;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/** Jedna instancja weryfikatora (pamięć podręczna JWKS) na aplikację. */
@ApplicationScoped
public class TokenVerifierProducer {

    private TokenVerifier verifier;

    @PostConstruct
    void init() {
        verifier = JwtRs256Verifier.fromSettings();
    }

    public TokenVerifier verifier() {
        return verifier;
    }

    /** Do testów. */
    public void override(TokenVerifier v) {
        this.verifier = v;
    }
}
