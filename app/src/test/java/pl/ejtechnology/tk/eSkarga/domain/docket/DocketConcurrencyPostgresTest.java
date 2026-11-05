package pl.ejtechnology.tk.eSkarga.domain.docket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import pl.ejtechnology.tk.eSkarga.support.DocketConcurrencyScenario;
import pl.ejtechnology.tk.eSkarga.support.JpaTestSupport;

/**
 * BR-01 współbieżnie na lokalnym PostgreSQL z rolą aplikacji – ten sam scenariusz co {@code DocketConcurrencyPostgresIT}
 * (Testcontainers), uruchamiany gdy testy serwisów skierowano na PostgreSQL ({@code -Deskarga.test.jdbcUrl=…}).
 * Na H2 pominięty: blokady wierszy H2 w pamięci nie odwzorowują zachowania produkcyjnej bazy.
 */
@EnabledIfSystemProperty(named = JpaTestSupport.PROP_URL, matches = ".+")
class DocketConcurrencyPostgresTest {

    static JpaTestSupport db;

    @BeforeAll
    static void start() {
        db = JpaTestSupport.create();
    }

    @AfterAll
    static void stop() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void piecdziesiatTransakcjiDziesiecWycofanychNumeryBezLuk() throws Exception {
        DocketConcurrencyScenario.piecdziesiatTransakcjiDziesiecWycofanych(db);
    }
}
