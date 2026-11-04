# Środowisko deweloperskie (Docker Compose)

```bash
cp infra/docker/.env.example infra/docker/.env   # uzupełnij hasła
mvn -B -DskipTests package
docker compose -f infra/docker/compose.yml up -d --build
docker compose -f infra/docker/compose.yml logs -f app
```

| Usługa | Adres | Logowanie testowe |
| --- | --- | --- |
| Aplikacja | http://localhost:8082/eskarga/ | logowanie przez Keycloak ID-users (:8180) |
| Cockpit Operaton | http://localhost:8080/operaton/ | `demo` / `demo` (dystrybucja) |
| Konsola WildFly | http://localhost:9990/ | `add-user.sh` w kontenerze |
| ID-users (Keycloak, realm `id-users`) | http://localhost:8180 | z projektu `id` (`dev/docker-compose.yml`); użytkownicy testowi ID-users z rolami eSkargi nadanymi przez `id-users-rejestracja` |
| S3 (MinIO) | http://localhost:9100 (API), http://localhost:9101 (konsola) | `S3_ACCESS_KEY` / `S3_SECRET_KEY`; kubełek `eskarga-akta` z Object Lock |
| TSA dev (RFC 3161) | http://localhost:3180 | certyfikat do magazynu zaufania: `/tsa.crt` |
| PostgreSQL | localhost:5432 | role z `.env` |
| OpenSearch | http://localhost:9200 | bez TLS (tylko dev) |
| WireMock | http://localhost:8089/__admin | – |

Tryb pracy nad interfejsem bez dostawcy tożsamości: `ESKARGA_SECURITY_MODE=demo` w `.env`
(tożsamość z danych przykładowych, wariant roli parametrem `?rola=ref|bp|asy|sed|pre|kie|arc|adm`).
Stan docelowy test/prod: wyłącznie `oidc`.

Uwaga: obraz `app` pobiera dystrybucję Operaton dla WildFly z GitHub Releases; wymaga dostępu do internetu przy budowie.
Demon Dockera nie był dostępny w sesji, w której powstał ten plik – konfiguracja nie była jeszcze uruchomiona.

## Uzupełnienia (partia 2)

- `flyway` – migracje schematu `repertorium` rolą migracji przed startem aplikacji (ADR 0016); lokalizacje `db/migration` i `db/migration-postgresql`.
- `opensearch` – obraz z wtyczką `analysis-stempel` (analizator polski, ADR 0018).
- profil `monitoring` – Prometheus (:9090) i Grafana (:3000) z panelem `infra/monitoring/grafana-dashboard.json`.
- workery: `ESKARGA_APP_API`, `ESKARGA_DOCS_API`, `ESKARGA_EXTERNAL_BASE_URL` (WireMock) – tematy `doreczenie-pisma`, `przekazanie-merytoryczne`, cykl `dokument-przetworz`.
- aplikacja: `ESKARGA_SEARCH_URL`, `ESKARGA_DOCS_URL`, `ESKARGA_DOCS_OIDC_*`, `ESKARGA_HSM_MODE`, `ESKARGA_METRICS_ALLOW`.

## ID-users lokalnie (http://localhost:8180/realms/id-users)

- Issuer w tokenach i w przeglądarce to zawsze `http://localhost:8180/realms/id-users` (`ESKARGA_OIDC_ISSUER`).
- Kontener aplikacji pyta o discovery, token i JWKS pod adresem `ESKARGA_OIDC_PROVIDERURI`: `http://oidc:8180/...` dla Keycloaka
  z tego Compose (uruchomionego z `--hostname-backchannel-dynamic=true`) albo `http://host.docker.internal:8180/...`, gdy ID-users
  działa na hoście (`OIDC_PROVIDER_URI` w `.env`, usługa `oidc` wtedy zbędna: `docker compose up -d --scale oidc=0`).
- W Keycloaku na hoście realm `id-users` musi mieć klientów jak w `keycloak/id-users-realm.json` (`eskarga`, `eskarga-workers`).

## Uruchomienie obok ID-users (bez konfliktów)

Środowisko ID-users (repozytorium `id`, `dev/docker-compose.yml`, projekt `id-users`) zajmuje porty 8080, 9990, 8180, 9000, 5432,
8025, 1025, 1389, 1636, 8200. eSkarga używa projektu `eskarga` i portów 8082 (aplikacja, Cockpit), 9992 (konsola WildFly), 5433
(PostgreSQL), 9100/9101 (MinIO), 9200, 3180, 8089, 9090, 3000 – wszystkie do zmiany w `.env` (`PORT_*`).

```bash
# 1. ID-users (w katalogu projektu id)
docker compose -f dev/docker-compose.yml --env-file dev/.env up -d --build
# 2. eSkarga (w tym katalogu) – rejestracja klientów w Keycloaku ID-users, potem start
docker compose --profile id-users run --rm id-users-rejestracja
docker compose up -d --build
```

Konta testowe ID-users z rolami eSkargi (hasło `DEV_USER_PASSWORD` z `dev/.env` projektu id): `marek.zielinski` referent,
`anna.nowak` biuro podawcze, `agnieszka.duda` asystent, `adam.kowal` sędzia i Prezes, `beata.sowa` kierownik, `katarzyna.lis`
archiwista, `marcin.kaminski` administrator; mapowanie zmienia zmienna `ESKARGA_UZYTKOWNICY`.
Skrypt najpierw sonduje `KC_URL/realms/master` i wypisuje odpowiedź; „Cannot parse the JSON” z kcadm oznacza, że pod
adresem nie odpowiada Keycloak. Alternatywa bez przechodzenia przez hosta (ta sama sieć co ID-users, jak w jego
własnym `keycloak-uzytkownicy`): `OIDC_PROVIDER_URI_ADMIN=http://wildfly:8180` w `.env` i
`docker compose -f infra/docker/compose.yml --profile id-users run --rm --network id-users_default id-users-rejestracja`.
Bez ID-users: `docker compose --profile oidc-local up -d` z `OIDC_PROVIDER_URI=http://oidc:8180/realms/id-users` (Keycloak eSkargi na :8181 – wtedy issuer też trzeba zmienić na `http://localhost:8181/realms/id-users`).

## Windows

Skrypty montowane do kontenerów (`infra/docker/**/*.sh`, `*.cli`) muszą mieć końcówki linii LF – wymusza to `.gitattributes`.
Jeśli repozytorium było sklonowane wcześniej z `core.autocrlf=true` (objaw: `set: pipefail\r: invalid option name`),
odśwież kopię roboczą: `git rm --cached -r . && git reset --hard`. Docker Desktop: włączony `host.docker.internal`
(domyślnie) – przez ten adres aplikacja i workery łączą się z Keycloakiem ID-users na hoście (:8180).
