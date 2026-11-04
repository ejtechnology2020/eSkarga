# eSkarga – Repertorium Ts/Tw

Opis projektu, stos technologiczny, zasady architektoniczne i konwencje kodu są w [`doc/CLAUDE.md`](doc/CLAUDE.md).
Lista zadań z notatkami z realizacji: [`doc/TASKS.md`](doc/TASKS.md). Decyzje: [`docs/adr/`](docs/adr/).

## Polecenia

```bash
mvn -B verify                                        # build + testy jednostkowe i procesowe (JUnit 5, operaton-bpm-assert, H2)
mvn -B verify -Pit                                   # testy integracyjne na PostgreSQL 17 (Testcontainers)
mvn -B verify -Psecurity                             # skan zależności OWASP Dependency-Check (CVSS ≥ 7 przerywa build)
cp infra/docker/.env.example infra/docker/.env       # uzupełnij hasła
docker compose -f infra/docker/compose.yml --profile id-users run --rm id-users-rejestracja   # klienci eSkargi w Keycloaku ID-users (:8180)
docker compose -f infra/docker/compose.yml up -d --build   # aplikacja http://localhost:8082/eskarga (porty obok ID-users: PORT_* w .env)
docker compose -f infra/docker/compose.yml logs -f app
docker compose -f infra/docker/compose.yml --profile monitoring up -d   # Prometheus/Grafana
cd ui-tests && npm ci && npm test                    # Playwright + axe (ESKARGA_URL)
```

Tryb pracy nad interfejsem bez ID-users: `-Deskarga.security.mode=demo -Deskarga.demo.today=2026-11-03`,
warianty ról i stanów ekranów parametrami `?rola=`, `?stan=`, `?widok=` (ADR 0004).

## Struktura

- `common/` – JAR: weryfikacja tokenów ID-users (RS256/JWKS), klient client credentials, odczyt konfiguracji.
- `app/` – WAR: Facelets (`src/main/webapp`), beany (`web`), domena (`domain`), proces (`process`, `resources/bpmn`),
  bezpieczeństwo (`security`), audyt (`audit`), dokumenty (`documents`), migracje Flyway (`resources/db/migration`).
- `docs-service/` – WAR: szablony pism (FreeMarker w piaskownicy), PDF/A (OpenHTMLtoPDF), walidacja.
- `workers/` – JAR: external tasks (doręczenia, przekazanie do SK/K/U), cykl przetwarzania dokumentów.
- `local-signer/` – JAR na stacji sędziego: podpis skrótu kluczem YubiKey (PKCS#11), HTTP na 127.0.0.1 (ADR 0008).
- `infra/docker/` – środowisko dev (Compose: WildFly+Operaton, PostgreSQL, Flyway, MinIO, OpenSearch, SoftHSM, Keycloak „id-users”, TSA dev, WireMock, monitoring);
  `infra/ansible/` – role dla VM; `infra/monitoring/` – Prometheus/Grafana/alerty; `infra/scripts/` – aktualizacja Operaton, diagram ER.
- `ui-tests/` – Playwright + axe-core (poza `mvn verify`).
