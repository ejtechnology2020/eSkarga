package pl.ejtechnology.tk.eSkarga.domain.docket;

import jakarta.persistence.EntityManager;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Pierwszy wiersz licznika w roku (BR-01). Dwie równoległe transakcje, które nie znalazły wiersza, nie mogą obie go
 * wstawić: na PostgreSQL {@code INSERT … ON CONFLICT DO NOTHING} (druga czeka na zatwierdzenie pierwszej i nic nie robi),
 * na innych bazach (H2 w testach) {@code INSERT … WHERE NOT EXISTS}. Po wstawieniu wywołujący zakłada blokadę
 * {@code SELECT … FOR UPDATE} jak dla istniejącego wiersza.
 */
public final class CounterRows {

    private CounterRows() {
    }

    /** Licznik sygnatur {@code docket_counter (symbol, year)} – wartość początkowa 0. */
    public static void ensureDocketCounter(EntityManager em, String symbol, int year) {
        String sql = isPostgres(em)
                ? "insert into {h-schema}docket_counter (symbol, year, last_value) values (:s, :y, 0) on conflict do nothing"
                : "insert into {h-schema}docket_counter (symbol, year, last_value) select :s, :y, 0 where not exists "
                        + "(select 1 from {h-schema}docket_counter where symbol = :s and year = :y)";
        em.createNativeQuery(sql).setParameter("s", symbol).setParameter("y", year).executeUpdate();
    }

    /** Licznik dziennika wpływu {@code incoming_counter (year)} – wartość początkowa 0. */
    public static void ensureIncomingCounter(EntityManager em, int year) {
        String sql = isPostgres(em)
                ? "insert into {h-schema}incoming_counter (year, last_value) values (:y, 0) on conflict do nothing"
                : "insert into {h-schema}incoming_counter (year, last_value) select :y, 0 where not exists "
                        + "(select 1 from {h-schema}incoming_counter where year = :y)";
        em.createNativeQuery(sql).setParameter("y", year).executeUpdate();
    }

    static boolean isPostgres(EntityManager em) {
        try {
            SessionFactoryImplementor sf = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
            return sf.getJdbcServices().getDialect() instanceof PostgreSQLDialect;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
