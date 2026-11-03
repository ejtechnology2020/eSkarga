package pl.ejtechnology.tk.eSkarga.docs.signature;

import java.net.URI;

import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/**
 * Znacznik czasu RFC 3161 (ADR 0023, decyzja 03.11.2026: produkcyjnie KIR Szafir): adres z {@code eskarga.docs.tsaUrl}
 * (dev: {@code http://tsa:3180/}; prod: usługa kwalifikowana KIR), uwierzytelnienie konta KIR przez
 * {@code eskarga.docs.tsaUser}/{@code eskarga.docs.tsaPassword} (HTTP Basic) albo certyfikat klienta
 * ({@code eskarga.docs.tsaKeyStore}, {@code eskarga.docs.tsaKeyStorePassword}, PKCS#12). Sekrety wyłącznie ze środowiska.
 * Brak adresu = podpis bez znacznika (PAdES-B-B, tylko dev).
 */
public final class TimestampSources {

    public static final String URL_PROPERTY = "eskarga.docs.tsaUrl";

    private TimestampSources() {
    }

    public static TSPSource fromSettings() {
        String url = Settings.value(URL_PROPERTY, "");
        if (url.isBlank()) {
            return null;
        }
        return build(url, Settings.value("eskarga.docs.tsaUser", ""), Settings.value("eskarga.docs.tsaPassword", ""),
                Settings.value("eskarga.docs.tsaKeyStore", ""), Settings.value("eskarga.docs.tsaKeyStorePassword", ""),
                Integer.parseInt(Settings.value("eskarga.docs.tsaTimeoutMs", "10000")));
    }

    static OnlineTSPSource build(String url, String user, String password, String keyStore, String keyStorePassword, int timeoutMs) {
        TimestampDataLoader loader = new TimestampDataLoader();
        loader.setTimeoutConnection(timeoutMs);
        loader.setTimeoutSocket(timeoutMs);
        if (!user.isBlank()) {
            URI uri = URI.create(url);
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            loader.addAuthentication(uri.getHost(), port, uri.getScheme(), user, password.toCharArray());
        }
        if (!keyStore.isBlank()) {
            loader.setSslKeystore(new eu.europa.esig.dss.model.FileDocument(keyStore));
            loader.setSslKeystoreType("PKCS12");
            loader.setSslKeystorePassword(keyStorePassword.toCharArray());
        }
        OnlineTSPSource tsp = new OnlineTSPSource(url);
        tsp.setDataLoader(loader);
        return tsp;
    }
}
