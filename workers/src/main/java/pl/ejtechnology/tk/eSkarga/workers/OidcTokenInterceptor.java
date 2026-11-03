package pl.ejtechnology.tk.eSkarga.workers;

import org.operaton.bpm.client.interceptor.ClientRequestContext;
import org.operaton.bpm.client.interceptor.ClientRequestInterceptor;

/**
 * Nagłówek {@code Authorization: Bearer} z tokenem ID-users (client credentials) do REST silnika (architektura §5.4).
 * Pobieranie i odświeżanie tokenu dostarcza T-042 po poznaniu kontraktu ID-users [zaślepka]; do tego czasu
 * bez tokenu, gdy adres tokenu jest pusty (dev z mock OIDC).
 */
public final class OidcTokenInterceptor implements ClientRequestInterceptor {

    private final WorkerConfig cfg;

    public OidcTokenInterceptor(WorkerConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public void intercept(ClientRequestContext requestContext) {
        if (cfg.oidcTokenUrl() != null && !cfg.oidcTokenUrl().isBlank()) {
            requestContext.addHeader("Authorization", "Bearer " + fetchToken());
        }
    }

    String fetchToken() {
        throw new UnsupportedOperationException("Kontrakt ID-users nieznany – implementacja w T-042");
    }
}
