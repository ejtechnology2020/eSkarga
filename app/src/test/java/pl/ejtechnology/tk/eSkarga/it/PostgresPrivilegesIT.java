package pl.ejtechnology.tk.eSkarga.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T-003 / NFR-01 na prawdziwym PostgreSQL 17: migracje rolą migracji przechodzą, a rola aplikacji nie ma UPDATE/DELETE
 * na wpisach repertorium i dzienniku zdarzeń, ma UPDATE na liczniku sygnatur i tylko na kolumnach kwalifikacji pisma.
 * Uruchamianie: {@code mvn -B verify -Pit} (wymaga Dockera).
 */
@Testcontainers
class PostgresPrivilegesIT {

    @Container
    static final PostgreSQLContainer<?> PG = PostgresSupport.container();

    @BeforeAll
    static void migrate() {
        PostgresSupport.migrate(PG);
    }

    private Connection app() throws SQLException {
        Connection c = DriverManager.getConnection(PostgresSupport.appUrl(PG), PostgresSupport.APP_USER, PostgresSupport.APP_PASSWORD);
        c.setAutoCommit(true);
        return c;
    }

    @Test
    void rolaAplikacjiDopisujeAleNieZmieniaWpisowRepertorium() throws SQLException {
        try (Connection c = app(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO case_file (docket_symbol, docket_number, docket_year, entered_on, created_by) VALUES ('Ts', 1, 2026, DATE '2026-11-03', 'jtestowy')");
            st.executeUpdate("INSERT INTO case_file_version (case_file_id, version, status, created_by) SELECT id, 1, 'ZAREJESTROWANA', 'jtestowy' FROM case_file");
            st.executeUpdate("INSERT INTO audit_event (occurred_at, user_id, action, prev_hash, hash) VALUES (now(), 'jtestowy', 'test', repeat('0', 64), repeat('1', 64))");

            assertThatThrownBy(() -> st.executeUpdate("UPDATE case_file SET created_by = 'x'")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("UPDATE case_file_version SET status = 'ZAWIESZONA'")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("DELETE FROM case_file_version")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("DELETE FROM case_file")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("UPDATE audit_event SET action = 'zmienione'")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("DELETE FROM audit_event")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("TRUNCATE audit_event")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("CREATE TABLE repertorium.zla (id int)")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    @Test
    void licznikSygnaturIKwalifikacjaPismaSaJedynymiZmianami() throws SQLException {
        try (Connection c = app(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO docket_counter (symbol, year, last_value) VALUES ('Tw', 2026, 0)");
            assertThat(st.executeUpdate("UPDATE docket_counter SET last_value = last_value + 1 WHERE symbol = 'Tw' AND year = 2026")).isEqualTo(1);
            st.executeUpdate("INSERT INTO incoming_letter (register_no, received_at, channel, sender, created_by) VALUES ('DW 2026/00001', now(), 'POCZTA', 'Jan Przykładowy', 'kprzykladowy')");
            assertThat(st.executeUpdate("UPDATE incoming_letter SET qualification = 'PISMO_OGOLNE' WHERE register_no = 'DW 2026/00001'")).isEqualTo(1);
            assertThatThrownBy(() -> st.executeUpdate("UPDATE incoming_letter SET sender = 'Ktoś inny'")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("DELETE FROM incoming_letter")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }
}
