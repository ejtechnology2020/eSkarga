package pl.ejtechnology.tk.eSkarga.workers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Konfiguracja ze zmiennych środowiskowych; sekrety nigdy w repozytorium. */
public record WorkerConfig(String engineRestUrl, String workerId, int maxTasks, long lockDurationMs, Set<String> topics,
                           String oidcTokenUrl, String oidcClientId, String oidcClientSecret, String wiremockBaseUrl) {

    public static WorkerConfig fromEnvironment() {
        String topics = env("ESKARGA_WORKER_TOPICS", String.join(",", Topics.DORECZENIE_ELEKTRONICZNE, Topics.DORECZENIE_POCZTOWE, Topics.PRZEKAZANIE_MERYTORYCZNE));
        return new WorkerConfig(
                env("ESKARGA_ENGINE_REST", "http://localhost:8080/engine-rest"),
                env("ESKARGA_WORKER_ID", "eskarga-worker-1"),
                Integer.parseInt(env("ESKARGA_WORKER_MAX_TASKS", "5")),
                Long.parseLong(env("ESKARGA_WORKER_LOCK_MS", "60000")),
                Arrays.stream(topics.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet()),
                env("ESKARGA_OIDC_TOKEN_URL", ""),
                env("ESKARGA_OIDC_CLIENT_ID", "eskarga-workers"),
                env("ESKARGA_OIDC_CLIENT_SECRET", ""),
                env("ESKARGA_EXTERNAL_BASE_URL", "http://localhost:8089"));
    }

    public boolean topicEnabled(String topic) {
        return topics.contains(topic);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
