# ADR 0016 – Migracje Flyway jako osobny krok wdrożenia; aplikacja bez UPDATE na wpisach repertorium

Status: zaakceptowana 03.11.2026 (migracje jako osobny krok, brak UPDATE)

## Kontekst

T-003 wymaga, by rola aplikacji nie miała UPDATE/DELETE na tabelach repertorium (NFR-01), a migracje wykonywała osobna
rola. Uruchamianie Flyway z aplikacji oznaczałoby drugie źródło danych z rolą o prawach DDL w WildFly oraz wyścig
z walidacją schematu przez JPA (`hbm2ddl=validate`) przy wdrożeniu.

## Decyzja

1. Migracje uruchamia **Flyway CLI** przed startem aplikacji: w dev usługa `flyway` w `compose.yml`
   (`service_completed_successfully`), na VM zadanie Ansible w roli `eskarga_app` (T-053). Lokalizacje:
   `db/migration` (wspólne, testowane na H2) i `db/migration-postgresql` (tylko PostgreSQL: GRANT/REVOKE).
2. Tabele **tylko do dopisywania**: `case_file`, `case_file_version`, `correction_decision`, `audit_event` – rola
   aplikacji ma na nich SELECT/INSERT. Decyzja o korekcie (UC-22) to nowy wiersz `correction_decision`, a status
   korekty wersji jest wyliczany, nie zapisywany. W `incoming_letter` wolno zmienić tylko kolumny kwalifikacji
   (`GRANT UPDATE (qualification, case_file_id)`).
3. UPDATE przysługuje tylko tabelom operacyjnym (doręczenia, terminy, dokumenty, szablony, słowniki, liczniki).
   Lista jest w `V5_1__uprawnienia_roli_aplikacji.sql`; test integracyjny `PostgresPrivilegesIT` (profil `-Pit`) sprawdza
   odmowę UPDATE/DELETE na wpisach i działanie `SELECT … FOR UPDATE` na liczniku.
4. `flyway-core` w module `app` pozostaje tylko w zakresie testów (H2).

## Konsekwencje

- Wdrożenie nowej wersji = (1) migracje CLI, (2) wdrożenie WAR. Rollback schematu nie jest automatyczny – migracje są
  tylko dopisujące (ADR 0011).
- Encje modyfikowane w kodzie poza listą UPDATE (np. nowe pola) wymagają nowej migracji z GRANT; walidacja na
  PostgreSQL w CI (profil `it`) wykryje brak uprawnienia jako błąd testu.
