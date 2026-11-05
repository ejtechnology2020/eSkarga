package pl.ejtechnology.tk.eSkarga.workers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Konfiguracja ze zmiennych środowiskowych (ADR 0010); sekrety nigdy w repozytorium. Uwierzytelnienie klienta
 * {@code eskarga-workers}: {@code ESKARGA_OIDC_PRIVATE_KEY_PEM} (klucz PKCS#8 z rejestracji w ID-users, {@code private_key_jwt}, gdy plik
 * istnieje) albo {@code ESKARGA_OIDC_CLIENT_SECRET}.
 */
public record WorkerConfig(String engineRestUrl, String workerId, int maxTasks, long lockDurationMs, Set<String> topics,
                           String oidcTokenUrl, String oidcClientId, String oidcClientSecret, String oidcScope, String oidcPrivateKeyPem,
                           String appApiUrl, String docsApiUrl, String externalBaseUrl, long documentPollSeconds) {

    public static WorkerConfig fromEnvironment() {
        String topics = env("ESKARGA_WORKER_TOPICS", String.join(",", Topics.DORECZENIE_PISMA, Topics.PRZEKAZANIE_MERYTORYCZNE, Topics.DOKUMENT_PRZETWORZ));
        return new WorkerConfig(
                env("ESKARGA_ENGINE_REST", "http://localhost:8080/engine-rest"),
                env("ESKARGA_WORKER_ID", "eskarga-worker-1"),
                Integer.parseInt(env("ESKARGA_WORKER_MAX_TASKS", "5")),
                Long.parseLong(env("ESKARGA_WORKER_LOCK_MS", "60000")),
                Arrays.stream(topics.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet()),
                env("ESKARGA_OIDC_TOKEN_URL", ""),
                env("ESKARGA_OIDC_CLIENT_ID", "eskarga-workers"),
                env("ESKARGA_OIDC_CLIENT_SECRET", ""),
                env("ESKARGA_OIDC_SCOPE", ""),
                env("ESKARGA_OIDC_PRIVATE_KEY_PEM", ""),
                env("ESKARGA_APP_API", "http://localhost:8080/eskarga/api"),
                env("ESKARGA_DOCS_API", "http://localhost:8080/eskarga-docs/api"),
                env("ESKARGA_EXTERNAL_BASE_URL", "http://localhost:8089"),
                Long.parseLong(env("ESKARGA_DOCUMENT_POLL_SECONDS", "30")));
    }

    public boolean topicEnabled(String topic) {
        return topics.contains(topic);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
