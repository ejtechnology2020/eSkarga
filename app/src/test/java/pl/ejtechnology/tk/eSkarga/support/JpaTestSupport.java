package pl.ejtechnology.tk.eSkarga.support;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import org.flywaydb.core.Flyway;

/**
 * Baza testowa (ADR 0011): migracje Flyway na H2 w trybie PostgreSQL, a potem Hibernate z {@code hbm2ddl=validate},
 * więc każdy test repozytorium sprawdza też zgodność encji z DDL.
 */
public final class JpaTestSupport implements AutoCloseable {

    private final String url;
    private final EntityManagerFactory emf;

    public JpaTestSupport() {
        this("rep" + System.nanoTime());
    }

    public JpaTestSupport(String dbName) {
        this.url = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR,DAY,VALUE,KEY;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").schemas("repertorium").defaultSchema("repertorium")
                .locations("classpath:db/migration").load().migrate();
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.transactionType", "RESOURCE_LOCAL");
        props.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        props.put("jakarta.persistence.jdbc.url", url);
        props.put("jakarta.persistence.jdbc.user", "sa");
        props.put("jakarta.persistence.jdbc.password", "");
        props.put("hibernate.default_schema", "repertorium");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");
        this.emf = Persistence.createEntityManagerFactory("repertorium", props);
    }

    public EntityManagerFactory emf() {
        return emf;
    }

    public String url() {
        return url;
    }

    /** Wykonuje blok w transakcji na świeżym EntityManagerze. */
    public <T> T tx(Function<EntityManager, T> work) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction t = em.getTransaction();
        try {
            t.begin();
            T result = work.apply(em);
            t.commit();
            return result;
        } catch (RuntimeException e) {
            if (t.isActive()) {
                t.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void run(Consumer<EntityManager> work) {
        tx(em -> {
            work.accept(em);
            return null;
        });
    }

    @Override
    public void close() {
        emf.close();
    }
}
