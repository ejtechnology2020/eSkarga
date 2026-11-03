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
        this(new ClientCredentialsTokenClient(cfg.oidcTokenUrl(), cfg.oidcClientId(), cfg.oidcClientSecret(), cfg.oidcScope()));
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
