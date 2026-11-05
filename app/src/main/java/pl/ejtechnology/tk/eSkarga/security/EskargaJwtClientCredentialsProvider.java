package pl.ejtechnology.tk.eSkarga.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Map;
import java.util.logging.Logger;

import org.wildfly.security.http.oidc.ClientCredentialsProvider;
import org.wildfly.security.http.oidc.OidcClientConfiguration;

import pl.ejtechnology.tk.eSkarga.common.security.ClientCredentialsTokenClient;

/**
 * Dostawca poświadczeń klienta dla mechanizmu Elytron OIDC ({@code oidc.json}: {@code credentials.provider = jwt-eskarga}),
 * ładowany przez {@code ServiceLoader} z WAR-a. Uwierzytelnia klienta {@code eskarga} metodą {@code private_key_jwt} (RFC 7523)
 * tak, jak wymaga rejestracja w ID-users (T-037): klucz prywatny PKCS#8 w PEM z katalogu wyników narzędzia rejestracji,
 * {@code kid} z pliku {@code <klient>.kid}. Wbudowany dostawca {@code jwt} Elytrona wymaga PKCS#12 i wpisuje w {@code aud} adres
 * tokenu z metadanych – z kontenera to {@code http://host.docker.internal:8180/…}, a Keycloak porównuje {@code aud} z adresem
 * frontowym realmu ({@code http://localhost:8180/…}) i odpowiada „Invalid token audience”. Tu {@code aud} jest konfigurowalne.
 *
 * <p>Konfiguracja (mapa {@code credentials.jwt-eskarga}): {@code private-key-pem} (ścieżka), opcjonalnie {@code key-id}
 * (domyślnie z pliku {@code .kid} obok klucza) i {@code audience} (domyślnie adres tokenu z metadanych dostawcy).
 */
public class EskargaJwtClientCredentialsProvider implements ClientCredentialsProvider {

    private static final Logger LOG = Logger.getLogger(EskargaJwtClientCredentialsProvider.class.getName());
    public static final String ID = "jwt-eskarga";
    static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private PrivateKey key;
    private String kid;
    private String audience;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(OidcClientConfiguration deployment, Object config) {
        if (!(config instanceof Map<?, ?> cfg)) {
            throw new IllegalStateException("credentials." + ID + " w oidc.json musi być obiektem z private-key-pem");
        }
        String pem = text(cfg.get("private-key-pem"));
        if (pem == null) {
            throw new IllegalStateException("credentials." + ID + ".private-key-pem nie ustawione (ESKARGA_OIDC_PRIVATEKEYPEM)");
        }
        Path plik = Path.of(pem);
        if (!Files.isRegularFile(plik)) {
            throw new IllegalStateException("Brak klucza prywatnego klienta " + deployment.getResourceName() + ": " + plik
                    + " (ID_USERS_KLUCZE_DIR → katalog wyników narzędzia aplikacje:eskarga projektu id)");
        }
        key = ClientCredentialsTokenClient.privateKeyFromPem(plik);
        String configuredKid = text(cfg.get("key-id"));
        kid = configuredKid != null ? configuredKid : ClientCredentialsTokenClient.keyIdNextTo(plik);
        audience = text(cfg.get("audience"));
        LOG.info(() -> "private_key_jwt klienta " + deployment.getResourceName() + ": klucz " + plik.getFileName() + ", kid "
                + (kid == null ? "(brak – Keycloak dobierze po algorytmie)" : kid) + ", aud " + (audience == null ? "(adres tokenu z metadanych)" : audience));
    }

    @Override
    public void setClientCredentials(OidcClientConfiguration deployment, Map<String, String> requestHeaders, Map<String, String> formParams) {
        String aud = audience != null ? audience : deployment.getTokenUrl();
        formParams.put("client_assertion_type", CLIENT_ASSERTION_TYPE);
        formParams.put("client_assertion", ClientCredentialsTokenClient.signedAssertion(deployment.getResourceName(), aud, key, kid));
    }

    /** Wartość tekstowa z mapy konfiguracji; puste i nierozwiązane {@code ${env.…}} traktowane jak brak. */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || s.startsWith("${") ? null : s;
    }
}
