package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import pl.ejtechnology.tk.eSkarga.support.DocketConcurrencyScenario;
import pl.ejtechnology.tk.eSkarga.support.JpaTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

/**
 * Dziennik wpływu (FR-01): 30 równoległych rejestracji w pustym roku – wyścig o pierwszy wiersz {@code incoming_counter} –
 * daje numery DW 2026/00001…00030 bez luk i powtórzeń. Tylko na PostgreSQL ({@code -Deskarga.test.jdbcUrl=…}).
 */
@EnabledIfSystemProperty(named = JpaTestSupport.PROP_URL, matches = ".+")
class IncomingCounterConcurrencyPostgresTest {

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
    void trzydziesciRownoleglychNumerowDziennikaBezLukIPowtorzen() throws Exception {
        List<String> numbers = DocketConcurrencyScenario.run(db, 30, i -> false,
                em -> Wiring.inject(new IncomingRegister(), em).nextRegisterNo(2026));
        assertThat(numbers).hasSize(30).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, 30).mapToObj(i -> String.format("DW 2026/%05d", i)).toList());
    }
}
