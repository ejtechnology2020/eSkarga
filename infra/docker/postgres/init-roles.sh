#!/bin/bash
# Role bazy (T-003, NFR-01): migracje mają pełne prawa w schemacie repertorium; aplikacja tylko INSERT/SELECT
# (UPDATE wyłącznie na docket_counter – licznik sygnatur, BR-01). Schemat operaton należy do roli aplikacji.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE ${DB_USER_MIGRATION} LOGIN PASSWORD '${DB_PASSWORD_MIGRATION}';
  CREATE ROLE ${DB_USER_APP} LOGIN PASSWORD '${DB_PASSWORD_APP}';
  CREATE SCHEMA repertorium AUTHORIZATION ${DB_USER_MIGRATION};
  CREATE SCHEMA operaton AUTHORIZATION ${DB_USER_APP};
  GRANT USAGE ON SCHEMA repertorium TO ${DB_USER_APP};
  ALTER DEFAULT PRIVILEGES FOR ROLE ${DB_USER_MIGRATION} IN SCHEMA repertorium GRANT SELECT, INSERT ON TABLES TO ${DB_USER_APP};
  ALTER DEFAULT PRIVILEGES FOR ROLE ${DB_USER_MIGRATION} IN SCHEMA repertorium GRANT USAGE, SELECT ON SEQUENCES TO ${DB_USER_APP};
  -- UPDATE licznika sygnatur nadaje migracja V1 po utworzeniu tabeli: GRANT UPDATE ON repertorium.docket_counter TO ${DB_USER_APP};
EOSQL
