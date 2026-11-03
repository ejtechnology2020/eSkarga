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
| Mock ID-users (OIDC) | http://localhost:8081/tk | dowolny login; role i `acr` z `oidc/mock-oauth2.json` |
| PostgreSQL | localhost:5432 | role z `.env` |
| S3 (SeaweedFS) | http://localhost:8333 | klucze z `.env` |
| OpenSearch | http://localhost:9200 | bez TLS (tylko dev) |
| WireMock | http://localhost:8089/__admin | – |

Tryb pracy nad interfejsem bez dostawcy tożsamości: `ESKARGA_SECURITY_MODE=demo` w `.env`
(tożsamość z danych przykładowych, wariant roli parametrem `?rola=ref|bp|asy|sed|pre|kie|arc|adm`).
Stan docelowy test/prod: wyłącznie `oidc`.

Uwaga: obraz `app` pobiera dystrybucję Operaton dla WildFly z GitHub Releases; wymaga dostępu do internetu przy budowie.
Demon Dockera nie był dostępny w sesji, w której powstał ten plik – konfiguracja nie była jeszcze uruchomiona.
