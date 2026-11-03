package pl.ejtechnology.tk.eSkarga.docs.signature;

import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/** Kwalifikowany znacznik czasu (PAdES-B-T): adres TSA z konfiguracji {@code eskarga.docs.tsaUrl}; brak = podpis B-B (dev). */
public final class TimestampSources {

    private TimestampSources() {
    }

    public static TSPSource fromSettings() {
        String url = Settings.value("eskarga.docs.tsaUrl", "");
        if (url.isBlank()) {
            return null;
        }
        OnlineTSPSource tsp = new OnlineTSPSource(url);
        tsp.setDataLoader(new TimestampDataLoader());
        return tsp;
    }
}
