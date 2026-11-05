package pl.ejtechnology.tk.eSkarga.support;

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

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService;

/**
 * BR-01 na prawdziwej bazie: 50 równoległych transakcji roli aplikacji startujących jednocześnie na pustym liczniku
 * (wyścig o pierwszy wiersz roku), 10 wycofanych → numery 1…40 bez luk. Wspólny dla IT z Testcontainers
 * i testu na lokalnym PostgreSQL ({@code -Deskarga.test.jdbcUrl}).
 */
public final class DocketConcurrencyScenario {

    private DocketConcurrencyScenario() {
    }

    public static void piecdziesiatTransakcjiDziesiecWycofanych(JpaTestSupport db) throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 3);
        List<Integer> numbers = run(db, 50, i -> i % 5 == 0,
                em -> Wiring.inject(new DocketNumberService(), em).nextNumber(DocketSymbol.TS, today).number());
        assertThat(numbers).hasSize(40).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 40).boxed().toList());
        assertThat(db.tx(em -> Wiring.inject(new DocketNumberService(), em).peekNextNumber(DocketSymbol.TS, today)).number()).isEqualTo(41);
    }

    public interface Job<T> {
        T run(jakarta.persistence.EntityManager em);
    }

    public static <T> List<T> run(JpaTestSupport db, int count, java.util.function.IntPredicate rollback, Job<T> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                boolean rb = rollback.test(i);
                Callable<T> c = () -> {
                    start.await();
                    try {
                        return db.tx(em -> {
                            T n = job.run(em);
                            if (rb) {
                                throw new IllegalStateException("wycofane");
                            }
                            return n;
                        });
                    } catch (IllegalStateException e) {
                        return null;
                    }
                };
                results.add(pool.submit(c));
            }
            start.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : results) {
                T n = f.get();
                if (n != null) {
                    out.add(n);
                }
            }
            return out;
        } finally {
            pool.shutdown();
        }
    }
}
