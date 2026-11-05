package pl.ejtechnology.tk.eSkarga.it;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pl.ejtechnology.tk.eSkarga.support.DocketConcurrencyScenario;
import pl.ejtechnology.tk.eSkarga.support.JpaTestSupport;

/**
 * BR-01 na PostgreSQL (T-012): 50 równoległych transakcji roli aplikacji, 10 wycofanych → numery 1…40 bez luk,
 * blokada wiersza licznika ({@code SELECT … FOR UPDATE}) i walidacja schematu JPA na prawdziwej bazie.
 */
@Testcontainers
class DocketConcurrencyPostgresIT {

    @Container
    static final PostgreSQLContainer<?> PG = PostgresSupport.container();

    static JpaTestSupport db;

    @BeforeAll
    static void start() {
        PostgresSupport.migrate(PG);
        db = new JpaTestSupport(PostgresSupport.appUrl(PG), "org.postgresql.Driver", PostgresSupport.APP_USER, PostgresSupport.APP_PASSWORD, false);
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
