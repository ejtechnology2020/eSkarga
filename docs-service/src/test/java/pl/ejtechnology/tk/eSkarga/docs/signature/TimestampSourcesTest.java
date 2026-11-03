package pl.ejtechnology.tk.eSkarga.docs.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR 0023: konfiguracja źródła znacznika czasu (KIR Szafir na produkcji) bez połączenia sieciowego. */
class TimestampSourcesTest {

    @Test
    void brakAdresuToBrakZnacznika() {
        System.clearProperty(TimestampSources.URL_PROPERTY);
        assertThat(TimestampSources.fromSettings()).isNull();
    }

    @Test
    void adresIUwierzytelnienieZKonfiguracji() {
        System.setProperty(TimestampSources.URL_PROPERTY, "https://www.ts.kir.com.pl/HttpTspServer");
        System.setProperty("eskarga.docs.tsaUser", "konto-tk");
        System.setProperty("eskarga.docs.tsaPassword", "sekret-ze-srodowiska");
        try {
            var tsp = TimestampSources.fromSettings();
            assertThat(tsp).isNotNull();
            assertThat(TimestampSources.build("http://tsa:3180/", "", "", "", "", 5000)).isNotNull();
        } finally {
            System.clearProperty(TimestampSources.URL_PROPERTY);
            System.clearProperty("eskarga.docs.tsaUser");
            System.clearProperty("eskarga.docs.tsaPassword");
        }
    }
}
