# ADR 0011 – Testy repozytoriów: Flyway + Hibernate na H2 w trybie PostgreSQL, Testcontainers w profilu `it`

- Status: przyjęte 3.11.2026

## Decyzja
Testy jednostkowe modelu JPA i usług domenowych uruchamiają migracje Flyway na H2 (`MODE=PostgreSQL`) i walidują mapowanie
(`hibernate.hbm2ddl.auto=validate`). Dzięki temu `mvn -B verify` sprawdza spójność encji z DDL bez Dockera.
Testy, które wymagają prawdziwego PostgreSQL (uprawnienia ról, `SELECT … FOR UPDATE` pod obciążeniem, Object Lock w S3, OpenSearch),
działają w profilu `-Pit` na Testcontainers.

## Konsekwencje
DDL w migracjach ogranicza się do składni wspólnej dla PostgreSQL i H2 w trybie zgodności (bez `JSONB`, `tsvector`, partycji).
