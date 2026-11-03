# Środowisko deweloperskie (Docker Compose)

```bash
cp infra/docker/.env.example infra/docker/.env   # uzupełnij hasła
mvn -B -DskipTests package
docker compose -f infra/docker/compose.yml up -d --build
docker compose -f infra/docker/compose.yml logs -f app
```

| Usługa | Adres | Logowanie testowe |
| --- | --- | --- |
| Aplikacja | http://localhost:8080/eskarga/ | przez mock OIDC (poniżej) |
| Cockpit Operaton | http://localhost:8080/operaton/ | `demo` / `demo` (dystrybucja) |
| Konsola WildFly | http://localhost:9990/ | `add-user.sh` w kontenerze |
| ID-users dev (Keycloak, realm `id-users`) | http://localhost:8180 | konsola `admin` / `KEYCLOAK_ADMIN_PASSWORD`; użytkownicy testowi `jtestowy` (referent), `kprzykladowa` (biuro podawcze), `pfikcyjny` (sędzia, Prezes), `atestowy` (asystent), `mprzykladowska` (kierownik), `aarchiwalna`, `admin.techniczny` – hasło dev `Test1234!` |
| S3 (MinIO) | http://localhost:9000 (API), http://localhost:9001 (konsola) | `S3_ACCESS_KEY` / `S3_SECRET_KEY`; kubełek `eskarga-akta` z Object Lock |
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
