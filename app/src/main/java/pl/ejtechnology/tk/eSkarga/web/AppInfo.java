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

    private final String version = valueOr("eskarga.version", "1.0.0-SNAPSHOT");
    private final String environment = valueOr("eskarga.environment", "DEV").toUpperCase();

    public String getVersion() {
        return version;
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
