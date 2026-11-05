package pl.ejtechnology.tk.eSkarga.workers;

import org.operaton.bpm.client.interceptor.ClientRequestContext;
import org.operaton.bpm.client.interceptor.ClientRequestInterceptor;

import pl.ejtechnology.tk.eSkarga.common.security.ClientCredentialsTokenClient;

/**
 * Nagłówek {@code Authorization: Bearer} z tokenem ID-users (client credentials) do REST silnika i API aplikacji
 * (architektura §5.4, T-042). Gdy adres tokenu jest pusty (dev bez ID-users), żądania idą bez nagłówka.
 */
public final class OidcTokenInterceptor implements ClientRequestInterceptor {

    private final ClientCredentialsTokenClient tokens;

    public OidcTokenInterceptor(WorkerConfig cfg) {
        this(tokenClient(cfg));
    }

    /** {@code private_key_jwt}, gdy wskazany plik klucza istnieje (rejestracja w ID-users), inaczej sekret klienta. */
    static ClientCredentialsTokenClient tokenClient(WorkerConfig cfg) {
        String pem = cfg.oidcPrivateKeyPem();
        if (pem != null && !pem.isBlank() && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(pem))) {
            java.nio.file.Path plik = java.nio.file.Path.of(pem);
            return new ClientCredentialsTokenClient(cfg.oidcTokenUrl(), cfg.oidcClientId(), null, cfg.oidcScope(),
                    ClientCredentialsTokenClient.privateKeyFromPem(plik), ClientCredentialsTokenClient.keyIdNextTo(plik));
        }
        return new ClientCredentialsTokenClient(cfg.oidcTokenUrl(), cfg.oidcClientId(), cfg.oidcClientSecret(), cfg.oidcScope());
    }

    public OidcTokenInterceptor(ClientCredentialsTokenClient tokens) {
        this.tokens = tokens;
    }

    @Override
    public void intercept(ClientRequestContext requestContext) {
        if (tokens.configured()) {
            requestContext.addHeader("Authorization", "Bearer " + tokens.token());
        }
    }

    public ClientCredentialsTokenClient tokens() {
        return tokens;
    }
}
