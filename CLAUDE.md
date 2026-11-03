# eSkarga – Repertorium Ts/Tw

Opis projektu, stos technologiczny, zasady architektoniczne i konwencje kodu są w [`doc/CLAUDE.md`](doc/CLAUDE.md).
Lista zadań z notatkami z realizacji: [`doc/TASKS.md`](doc/TASKS.md). Decyzje: [`docs/adr/`](docs/adr/).

## Polecenia

```bash
mvn -B verify                                        # build + testy jednostkowe i procesowe (JUnit 5, operaton-bpm-assert, H2)
mvn -B verify -Pit                                   # testy integracyjne (Testcontainers) – profil przygotowany, testów jeszcze brak
mvn -B verify -Psecurity                             # skan zależności OWASP Dependency-Check (CVSS ≥ 7 przerywa build)
cp infra/docker/.env.example infra/docker/.env       # uzupełnij hasła
docker compose -f infra/docker/compose.yml up -d --build
docker compose -f infra/docker/compose.yml logs -f app
```

Tryb pracy nad interfejsem bez ID-users: `-Deskarga.security.mode=demo -Deskarga.demo.today=2026-11-03`,
warianty ról i stanów ekranów parametrami `?rola=`, `?stan=`, `?widok=` (ADR 0004).

## Struktura

- `app/` – WAR: Facelets (`src/main/webapp`), beany (`web`), domena (`domain`), proces (`process`, `resources/bpmn`),
  bezpieczeństwo (`security`), audyt (`audit`), dokumenty (`documents`), migracje Flyway (`resources/db/migration`).
- `docs-service/` – WAR: szablony pism (FreeMarker w piaskownicy), PDF/A (OpenHTMLtoPDF), walidacja.
- `workers/` – JAR: external tasks (doręczenia, przekazanie do SK/K/U).
- `infra/docker/` – środowisko dev; `infra/ansible/` – role dla VM (T-053).
