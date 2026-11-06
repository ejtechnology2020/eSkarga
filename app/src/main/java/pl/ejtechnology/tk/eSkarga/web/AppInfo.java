package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Informacje o aplikacji dla stopki i pasa środowiska. Środowisko przychodzi z property systemowej
 * {@code eskarga.environment} (PRODUKCJA, TEST, DEV); „TEST” pokazuje wyraźny pas na górze ekranu.
 */
@Named("appInfo")
@ApplicationScoped
public class AppInfo implements Serializable {

    private final java.util.Properties build = loadBuild();
    private final String version = valueOr("eskarga.version", build.getProperty("build.version", "1.0.0-SNAPSHOT"));
    private final String environment = valueOr("eskarga.environment", "DEV").toUpperCase();

    public String getVersion() {
        return version;
    }

    /** Commit i czas budowy WAR-a (eskarga-build.properties wypełniane przez Maven) – żeby w stopce było widać, co jest wdrożone. */
    public String getCommit() {
        return commit(build);
    }

    /** Skrócony commit z git-commit-id-maven-plugin; bez katalogu .git właściwość zostaje nierozwinięta → „lokalnie”; „*” = niezatwierdzone zmiany. */
    static String commit(java.util.Properties p) {
        String c = p.getProperty("build.commit", "").trim();
        if (c.isEmpty() || c.startsWith("${")) {
            c = "lokalnie";
        }
        return "true".equalsIgnoreCase(p.getProperty("build.dirty", "")) ? c + "*" : c;
    }

    /** Czas budowy w strefie Warszawy (Maven przy budowie odtwarzalnej podaje ISO-8601 w UTC). */
    public String getBuildTime() {
        String raw = build.getProperty("build.time", "?");
        try {
            return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(java.time.Instant.parse(raw).atZone(java.time.ZoneId.of("Europe/Warsaw")));
        } catch (java.time.format.DateTimeParseException e) {
            return raw;
        }
    }

    public String getBuild() {
        return getCommit() + " · " + getBuildTime();
    }

    /** Krótki znacznik buildu do adresów zasobów (VersionedResourceHandler): commit + czas budowy; „lokalnie” też się zmienia co build. */
    public static String buildStamp() {
        return Stamp.VALUE;
    }

    private static final class Stamp {
        static final String VALUE;
        static {
            java.util.Properties p = loadBuild();
            VALUE = Integer.toHexString((commit(p) + "@" + p.getProperty("build.time", "?")).hashCode());
        }
    }

    private static java.util.Properties loadBuild() {
        java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = AppInfo.class.getResourceAsStream("/eskarga-build.properties")) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (java.io.IOException ignored) {
            // bez pliku stopka pokazuje „?” – nie blokujemy startu aplikacji
        }
        return p;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isProduction() {
        return "PRODUKCJA".equals(environment);
    }

    public boolean isTest() {
        return !isProduction();
    }

    public String getFooterEnvClass() {
        return isProduction() ? "env" : "env test";
    }

    private static String valueOr(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return v == null || v.isBlank() ? def : v;
    }
}
