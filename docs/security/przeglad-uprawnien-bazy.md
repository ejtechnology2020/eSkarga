# Przegląd uprawnień bazy danych (T-003, T-055, NFR-01)

| Rola | Zakres | Uwagi |
| --- | --- | --- |
| `eskarga_migration` | właściciel schematu `repertorium`, DDL, pełne DML | tylko Flyway CLI w kroku wdrożenia (ADR 0016); hasło w Vault/.env |
| `eskarga_app` | `USAGE` na `repertorium`; `SELECT`, `INSERT` na wszystkich tabelach; `UPDATE` tylko na tabelach operacyjnych; właściciel schematu `operaton` | brak `UPDATE`/`DELETE`/`TRUNCATE` na `case_file`, `case_file_version`, `correction_decision`, `audit_event`; `UPDATE` kolumnowy na `incoming_letter` (kwalifikacja) |
| `replicator` | replikacja fizyczna | Patroni |
| `postgres` | superużytkownik | tylko administracja, bez dostępu aplikacji |

Lista tabel z `UPDATE` dla roli aplikacji: `app/src/main/resources/db/migration-postgresql/V5_1__uprawnienia_roli_aplikacji.sql`.
Test: `PostgresPrivilegesIT` (profil `-Pit`). Przegląd powtarzać przy każdej migracji dodającej tabelę (nowa tabela bez GRANT UPDATE = tylko dopisywanie).
