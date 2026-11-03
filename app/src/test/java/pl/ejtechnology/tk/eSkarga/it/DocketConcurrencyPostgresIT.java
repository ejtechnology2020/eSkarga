package pl.ejtechnology.tk.eSkarga.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService;
import pl.ejtechnology.tk.eSkarga.support.JpaTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

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
        LocalDate today = LocalDate.of(2026, 11, 3);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DocketNumber>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            boolean rollback = i % 5 == 0;
            Callable<DocketNumber> job = () -> {
                start.await();
                try {
                    return db.tx(em -> {
                        DocketNumber n = Wiring.inject(new DocketNumberService(), em).nextNumber(DocketSymbol.TS, today);
                        if (rollback) {
                            throw new IllegalStateException("wycofane");
                        }
                        return n;
                    });
                } catch (IllegalStateException e) {
                    return null;
                }
            };
            results.add(pool.submit(job));
        }
        start.countDown();
        List<Integer> numbers = new ArrayList<>();
        for (Future<DocketNumber> f : results) {
            DocketNumber n = f.get();
            if (n != null) {
                numbers.add(n.number());
            }
        }
        pool.shutdown();
        assertThat(numbers).hasSize(40).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 40).boxed().toList());
        assertThat(db.tx(em -> Wiring.inject(new DocketNumberService(), em).peekNextNumber(DocketSymbol.TS, today)).number()).isEqualTo(41);
    }
}
