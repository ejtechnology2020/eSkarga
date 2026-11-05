package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.security.http.oidc.ClientCredentialsProvider;
import org.wildfly.security.http.oidc.OidcClientConfiguration;

/** private_key_jwt klienta eskarga dla Elytron OIDC: klucz PEM i kid z rejestracji ID-users, aud = publiczny adres tokenu (T-004, ADR 0002). */
class EskargaJwtClientCredentialsProviderTest {

    @Test
    void asercjaZKluczaPemKidIAudienceZKonfiguracji(@TempDir Path dir) throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Path pem = dir.resolve("eskarga.key.pem");
        Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        Files.writeString(dir.resolve("eskarga.kid"), "eskarga-2026-11-04\n");
        OidcClientConfiguration deployment = mock(OidcClientConfiguration.class);
        when(deployment.getResourceName()).thenReturn("eskarga");
        when(deployment.getTokenUrl()).thenReturn("http://host.docker.internal:8180/realms/id-users/protocol/openid-connect/token");

        EskargaJwtClientCredentialsProvider p = new EskargaJwtClientCredentialsProvider();
        p.init(deployment, Map.of("private-key-pem", pem.toString(), "audience", "http://localhost:8180/realms/id-users/protocol/openid-connect/token"));
        Map<String, String> form = new HashMap<>();
        p.setClientCredentials(deployment, new HashMap<>(), form);

        assertThat(form).containsEntry("client_assertion_type", EskargaJwtClientCredentialsProvider.CLIENT_ASSERTION_TYPE);
        String[] czesci = form.get("client_assertion").split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(czesci[0]), StandardCharsets.UTF_8)).contains("\"kid\":\"eskarga-2026-11-04\"");
        assertThat(new String(Base64.getUrlDecoder().decode(czesci[1]), StandardCharsets.UTF_8))
                .contains("\"aud\":\"http://localhost:8180/realms/id-users/protocol/openid-connect/token\"").contains("\"sub\":\"eskarga\"");

        // bez audience – adres tokenu z metadanych; nierozwiązane ${env...} traktowane jak brak
        EskargaJwtClientCredentialsProvider bezAud = new EskargaJwtClientCredentialsProvider();
        bezAud.init(deployment, Map.of("private-key-pem", pem.toString(), "audience", "${env.ESKARGA_OIDC_ASSERTIONAUDIENCE:}"));
        Map<String, String> form2 = new HashMap<>();
        bezAud.setClientCredentials(deployment, new HashMap<>(), form2);
        assertThat(new String(Base64.getUrlDecoder().decode(form2.get("client_assertion").split("\\.")[1]), StandardCharsets.UTF_8))
                .contains("\"aud\":\"http://host.docker.internal:8180/realms/id-users/protocol/openid-connect/token\"");
    }

    @Test
    void brakKluczaToCzytelnyBlad(@TempDir Path dir) {
        OidcClientConfiguration deployment = mock(OidcClientConfiguration.class);
        when(deployment.getResourceName()).thenReturn("eskarga");
        assertThatThrownBy(() -> new EskargaJwtClientCredentialsProvider().init(deployment, Map.of("private-key-pem", dir.resolve("brak.key.pem").toString())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ID_USERS_KLUCZE_DIR");
        assertThatThrownBy(() -> new EskargaJwtClientCredentialsProvider().init(deployment, "tekst")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zarejestrowanyPrzezServiceLoader() {
        assertThat(ServiceLoader.load(ClientCredentialsProvider.class).stream().map(ServiceLoader.Provider::get).map(ClientCredentialsProvider::getId))
                .contains(EskargaJwtClientCredentialsProvider.ID);
    }
}
