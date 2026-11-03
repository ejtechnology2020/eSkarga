package pl.ejtechnology.tk.eSkarga.domain.docket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;

/**
 * BR-01 (T-012): 50 równoległych transakcji, w tym 10 wycofanych, daje numery 1…40 bez luk; numer nigdy nie jest użyty
 * ponownie. Repozytorium w pamięci odtwarza semantykę {@code SELECT … FOR UPDATE}: blokada wiersza trzymana do końca
 * transakcji, a wycofanie przywraca poprzednią wartość licznika.
 */
class DocketNumberAllocatorTest {

    /** Emulacja wiersza licznika z blokadą do końca transakcji. */
    static final class LockingRepo implements DocketCounterRepository {
        final Map<String, Integer> committed = new HashMap<>();
        final ReentrantLock rowLock = new ReentrantLock(true);
        final ThreadLocal<Integer> pending = new ThreadLocal<>();

        @Override
        public int lockAndGetLastValue(DocketSymbol symbol, int year) {
            rowLock.lock();
            return committed.getOrDefault(symbol.code() + year, 0);
        }

        @Override
        public void updateLastValue(DocketSymbol symbol, int year, int newValue) {
            pending.set(newValue);
        }

        void commit(DocketSymbol symbol, int year) {
            Integer v = pending.get();
            if (v != null) {
                committed.put(symbol.code() + year, v);
            }
            pending.remove();
            rowLock.unlock();
        }

        void rollback() {
            pending.remove();
            rowLock.unlock();
        }
    }

    @Test
    void fiftyConcurrentTransactionsWithTenRollbacksProduceGaplessSequence() throws Exception {
        LockingRepo repo = new LockingRepo();
        DocketNumberAllocator allocator = new DocketNumberAllocator(repo);
        LocalDate day = LocalDate.of(2026, 11, 3);
        List<Integer> committedNumbers = Collections.synchronizedList(new ArrayList<>());
        List<Integer> rolledBack = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(50);
        for (int i = 0; i < 50; i++) {
            final boolean rollback = i % 5 == 0; // 10 z 50 wycofanych
            pool.submit(() -> {
                try {
                    start.await();
                    DocketNumber n = allocator.next(DocketSymbol.TS, day);
                    if (rollback) {
                        rolledBack.add(n.number());
                        repo.rollback();
                    } else {
                        committedNumbers.add(n.number());
                        repo.commit(DocketSymbol.TS, 2026);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(committedNumbers).hasSize(40);
        assertThat(committedNumbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 40).boxed().toList());
        assertThat(rolledBack).hasSize(10);
        assertThat(repo.committed.get("Ts2026")).isEqualTo(40);
    }

    @Test
    void numberingRestartsEveryYear() {
        LockingRepo repo = new LockingRepo();
        DocketNumberAllocator allocator = new DocketNumberAllocator(repo);
        DocketNumber a = allocator.next(DocketSymbol.TS, LocalDate.of(2026, 12, 31));
        repo.commit(DocketSymbol.TS, 2026);
        DocketNumber b = allocator.next(DocketSymbol.TS, LocalDate.of(2027, 1, 2));
        repo.commit(DocketSymbol.TS, 2027);
        assertThat(a.format()).isEqualTo("Ts 1/26");
        assertThat(b.format()).isEqualTo("Ts 1/27");
    }

    @Test
    void symbolsAreCountedSeparately() {
        LockingRepo repo = new LockingRepo();
        DocketNumberAllocator allocator = new DocketNumberAllocator(repo);
        allocator.next(DocketSymbol.TS, LocalDate.of(2026, 1, 5));
        repo.commit(DocketSymbol.TS, 2026);
        DocketNumber tw = allocator.next(DocketSymbol.TW, LocalDate.of(2026, 1, 5));
        repo.commit(DocketSymbol.TW, 2026);
        assertThat(tw.format()).isEqualTo("Tw 1/26");
    }

    @Test
    void refusesSymbolsNotManagedHere() {
        DocketNumberAllocator allocator = new DocketNumberAllocator(new LockingRepo());
        assertThatThrownBy(() -> allocator.next(DocketSymbol.SK, LocalDate.of(2026, 1, 5))).isInstanceOf(IllegalArgumentException.class);
    }
}
