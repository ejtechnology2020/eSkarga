-- Odpowiednik infra/docker/postgres/init-roles.sh dla testów integracyjnych (hasła tylko testowe).
CREATE ROLE eskarga_migration LOGIN PASSWORD 'migracja-test';
CREATE ROLE eskarga_app LOGIN PASSWORD 'aplikacja-test';
CREATE SCHEMA repertorium AUTHORIZATION eskarga_migration;
CREATE SCHEMA operaton AUTHORIZATION eskarga_app;
GRANT USAGE ON SCHEMA repertorium TO eskarga_app;
ALTER DEFAULT PRIVILEGES FOR ROLE eskarga_migration IN SCHEMA repertorium GRANT SELECT, INSERT ON TABLES TO eskarga_app;
ALTER DEFAULT PRIVILEGES FOR ROLE eskarga_migration IN SCHEMA repertorium GRANT USAGE, SELECT ON SEQUENCES TO eskarga_app;
