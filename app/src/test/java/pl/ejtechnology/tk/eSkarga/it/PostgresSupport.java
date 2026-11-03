package pl.ejtechnology.tk.eSkarga.it;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/** Kontener PostgreSQL 17 z rolami jak w dev; migracje rolą migracji z obu lokalizacji (ADR 0016). */
final class PostgresSupport {

    static final String MIGRATION_USER = "eskarga_migration";
    static final String MIGRATION_PASSWORD = "migracja-test";
    static final String APP_USER = "eskarga_app";
    static final String APP_PASSWORD = "aplikacja-test";

    private PostgresSupport() {
    }

    static PostgreSQLContainer<?> container() {
        return new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("eskarga").withInitScript("it/init-roles.sql");
    }

    static void migrate(PostgreSQLContainer<?> pg) {
        Flyway.configure().dataSource(pg.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD)
                .schemas("repertorium").defaultSchema("repertorium")
                .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .placeholders(java.util.Map.of("app_role", APP_USER))
                .load().migrate();
    }

    static String appUrl(PostgreSQLContainer<?> pg) {
        return pg.getJdbcUrl() + (pg.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=repertorium";
    }
}
