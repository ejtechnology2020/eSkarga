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

    /** Właściwości systemowe kierujące testy serwisów na prawdziwy PostgreSQL z rolą aplikacji (zamiast H2). */
    public static final String PROP_URL = "eskarga.test.jdbcUrl";
    public static final String PROP_USER = "eskarga.test.user";
    public static final String PROP_PASSWORD = "eskarga.test.password";
    public static final String PROP_MIGRATION_USER = "eskarga.test.migrationUser";
    public static final String PROP_MIGRATION_PASSWORD = "eskarga.test.migrationPassword";

    /**
     * Baza dla klasy testowej: H2 w pamięci (domyślnie) albo – gdy ustawiono {@value #PROP_URL} – PostgreSQL z rolą aplikacji
     * (bez praw DDL, jak w WildFly), ze schematem odtwarzanym rolą migracji przed każdą klasą ({@code clean} + {@code migrate}
     * z obu lokalizacji migracji i nadaniami dla {@code ${app_role}}). Tak samo działają ITy z Testcontainers; lokalnie:
     * {@code mvn -pl app test -Deskarga.test.jdbcUrl=jdbc:postgresql://localhost:5432/eskarga_it?currentSchema=repertorium
     * -Deskarga.test.user=eskarga_app -Deskarga.test.password=… -Deskarga.test.migrationUser=eskarga_migration -Deskarga.test.migrationPassword=…}.
     */
    public static JpaTestSupport create() {
        String url = System.getProperty(PROP_URL);
        if (url == null || url.isBlank()) {
            return new JpaTestSupport();
        }
        String migUser = System.getProperty(PROP_MIGRATION_USER);
        if (migUser != null && !migUser.isBlank()) {
            Flyway flyway = Flyway.configure().dataSource(url, migUser, System.getProperty(PROP_MIGRATION_PASSWORD, "")).schemas("repertorium").defaultSchema("repertorium")
                    .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                    .placeholders(Map.of("app_role", System.getProperty(PROP_USER, "eskarga_app")))
                    .cleanDisabled(false).load();
            flyway.clean();
            flyway.migrate();
        }
        return new JpaTestSupport(url, "org.postgresql.Driver", System.getProperty(PROP_USER, "eskarga_app"), System.getProperty(PROP_PASSWORD, ""), false);
    }

    public JpaTestSupport(String dbName) {
        this("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR,DAY,VALUE,KEY;DB_CLOSE_DELAY=-1",
                "org.h2.Driver", "sa", "", true);
    }

    /**
     * Dowolna baza (np. PostgreSQL z Testcontainers). Gdy {@code migrate} jest false, schemat musi już istnieć –
     * tak działa aplikacja na roli bez praw DDL (ADR 0016).
     */
    public JpaTestSupport(String url, String driver, String user, String password, boolean migrate) {
        this.url = url;
        if (migrate) {
            Flyway.configure().dataSource(url, user, password).schemas("repertorium").defaultSchema("repertorium")
                    .locations("classpath:db/migration").load().migrate();
        }
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.transactionType", "RESOURCE_LOCAL");
        props.put("jakarta.persistence.jdbc.driver", driver);
        props.put("jakarta.persistence.jdbc.url", url);
        props.put("jakarta.persistence.jdbc.user", user);
        props.put("jakarta.persistence.jdbc.password", password);
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
