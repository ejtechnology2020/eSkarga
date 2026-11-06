# Środowisko deweloperskie (Docker Compose)

```bash
cp infra/docker/.env.example infra/docker/.env   # uzupełnij hasła
mvn -B clean -DskipTests package
docker compose -f infra/docker/compose.yml up -d --build
docker compose -f infra/docker/compose.yml logs -f app
```

| Usługa | Adres | Logowanie testowe |
| --- | --- | --- |
| Aplikacja | http://localhost:8082/eskarga/ | logowanie przez Keycloak ID-users (:8180) |
| Cockpit Operaton | http://localhost:8080/operaton/ | `demo` / `demo` (dystrybucja) |
| Konsola WildFly | http://localhost:9990/ | `add-user.sh` w kontenerze |
| ID-users (Keycloak, realm `id-users`) | http://localhost:8180 | z projektu `id` (`dev/docker-compose.yml`); użytkownicy testowi ID-users z rolami eSkargi nadanymi przez `id-users-rejestracja` |
| S3 (RustFS) | http://localhost:9100 (API), http://localhost:9101 (konsola) | `S3_ACCESS_KEY` / `S3_SECRET_KEY`; kubełek `eskarga-akta` z Object Lock |
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
(PostgreSQL), 9100/9101 (RustFS), 9200, 3180, 8089, 9090, 3000 – wszystkie do zmiany w `.env` (`PORT_*`).

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
Skrypt nie używa kcadm – rejestruje przez Admin REST API bezpośrednio z Basha (`/dev/tcp`, obraz nie ma curl): sonduje
`KC_URL/realms/master`, pobiera token administratora (password grant `admin-cli`, odświeżany co 30 s) i wykonuje idempotentne
GET/POST/PUT; przy statusie innym niż 2xx wypisuje odpowiedź i przerywa. Powód: w Keycloaku ID-users każde żądanie kcadm
(Apache HttpClient, także `get clients` z poprawnym `--token`) kończy się błędem serwera „Cannot parse the JSON
[unknown_error]” (JsonProcessingException w KeycloakErrorHandler), a identyczne żądania z Basha przechodzą. Przyczyna
nieustalona (do sprawdzenia w logu Keycloaka ID-users); ryzyko: Elytron OIDC w WildFly eSkargi też korzysta z Apache
HttpClient – zweryfikować przy pierwszym logowaniu do aplikacji. Alternatywa bez przechodzenia przez hosta (ta sama sieć
co ID-users, jak w jego własnym `keycloak-uzytkownicy`): `OIDC_PROVIDER_URI_ADMIN=http://wildfly:8180` w `.env` i
`docker compose -f infra/docker/compose.yml --profile id-users run --rm --network id-users_default id-users-rejestracja`.
Bez ID-users: `docker compose --profile oidc-local up -d` z `OIDC_PROVIDER_URI=http://oidc:8180/realms/id-users` (Keycloak eSkargi na :8181 – wtedy issuer też trzeba zmienić na `http://localhost:8181/realms/id-users`).

## Windows – szybki start

Wymagania: **Windows 10/11 z Docker Desktop** (backend WSL 2, co najmniej 8 GB pamięci dla Dockera razem z ID-users) i Git.
Javy ani Mavena nie trzeba instalować – gdy ich brak, build odbywa się w kontenerze `maven:3.9-eclipse-temurin-21`
(zależności w wolumenie `eskarga-m2`). Skrypty są odwzorowaniem `dev\windows\` projektu ID-users.

1. Uruchom ID-users (projekt `id`: `dev\windows\start.cmd`) – eSkarga loguje przez jego Keycloak na `localhost:8180`.
2. Kliknij dwukrotnie `infra\windows\start.cmd` (albo w PowerShell: `.\infra\windows\start.ps1`).

Pierwsze uruchomienie trwa 10–20 minut (dystrybucja Operaton, obrazy, zależności Maven). Po starcie skrypt wypisuje adresy
i konta testowe oraz otwiera <http://localhost:8082/eskarga>.

| Skrypt (`infra\windows\`) | Działanie |
| --- | --- |
| `start.cmd` / `start.ps1 [-Przebuduj] [-BezPrzegladarki] [-BezRejestracji] [-Monitoring]` | sprawdza Docker, ID-users i porty, tworzy `infra\docker\.env`, buduje artefakty Mavenem (gdy ich brak lub `-Przebuduj`), rejestruje klientów w ID-users, uruchamia usługi i czeka na gotowość (`/eskarga/health`) |
| `stop.cmd` | zatrzymuje kontenery (dane zostają) |
| `status.cmd` | stan kontenerów i punktów zdrowia (aplikacja, Cockpit, RustFS, OpenSearch, WireMock, Keycloak ID-users) |
| `logi.cmd [usługa] [-Sledz]` | logi, np. `logi.cmd app -Sledz` |
| `wdroz.cmd [-TylkoApp] [-Testy]` | build Mavenem i ponowne wdrożenie po zmianach w kodzie (restart WildFly, przebudowa obrazu workerów) |
| `rejestracja.cmd` | ponowna rejestracja klientów `eskarga`/`eskarga-workers` i ról użytkowników testowych w Keycloaku ID-users (idempotentna) |
| `reset.cmd [-Wymus] [-ZObrazami] [-ZMaven]` | usuwa kontenery i dane (z potwierdzeniem); klienci w Keycloaku ID-users zostają |
| `buduj.cmd [-Szybko] [-WDockerze]` | `mvn -B verify` (196 testów) albo `-Szybko` = samo pakowanie; lokalny JDK 21 lub kontener Maven |

Rozwiązywanie problemów na Windows:

- **Porty zajęte** – start wypisuje, które; zmień je w `infra\docker\.env` (`PORT_APP`, `PORT_DB` …). Po zmianie `PORT_APP`
  ustaw też `ESKARGA_PUBLIC_URL` i uruchom `rejestracja.cmd` (adresy callback i back-channel w Keycloaku).
- **„running scripts is disabled”** – użyj plików `.cmd` (uruchamiają PowerShell z `-ExecutionPolicy Bypass`).
- **ID-users nie odpowiada** – start przerywa z adresem, którego oczekuje; uruchom projekt `id` albo ustaw
  `ESKARGA_SECURITY_MODE=demo` (interfejs bez logowania, dane fikcyjne, ADR 0004).
- **Błędy `\r` w kontenerach** (`set: pipefail\r: invalid option name`) – `.gitattributes` wymusza LF dla skryptów `.sh`;
  po wcześniejszym klonowaniu z `core.autocrlf=true` wykonaj `git rm --cached -r . && git reset --hard`.
- **Wersje obrazów** (`*_IMAGE`, `OPERATON_VERSION`) są zarządzane przez repozytorium – skrypty aktualizują je w istniejącym
  `.env` i wypisują zmiany; porty i hasła zostają.

## Logowanie przez ID-users – jak to działa i gdzie szukać

1. Strona `*.xhtml` bez zalogowanego użytkownika → `LoginFilter` zapisuje adres w sesji i przekierowuje na `/eskarga/callback`
   (adres zwrotny zarejestrowany w ID-users; rejestr ID-users synchronizuje go do Keycloaka i nadpisuje inne wartości).
2. `/callback` jest zasobem chronionym w `web.xml`; mechanizm Elytron OIDC (`WEB-INF/oidc.json`) odsyła przeglądarkę
   do `http://localhost:8180/realms/id-users/protocol/openid-connect/auth?...&redirect_uri=.../eskarga/callback&code_challenge_method=S256`.
3. Po zalogowaniu (i MFA – klient `eskarga` ma `default.acr.values=mfa`) Keycloak wraca na `/eskarga/callback?code=...`,
   Elytron wymienia kod na tokeny, a serwlet przekierowuje na zapamiętany adres (domyślnie pulpit). Wylogowanie: `/wyloguj`
   (Elytron, `end_session` w ID-users) → powrót na `/eskarga/` (adres po wylogowaniu z rejestru ID-users) → strona „sesja zakończona”.
4. Brak ról w tokenie → strona 403 i wpis `WARNING … bez ról eSkargi` w logu z zawartością `resource_access`/`realm_access`.

Uwierzytelnienie klienta `eskarga` przy wymianie kodu na token: jeśli eSkarga została zarejestrowana w ID-users narzędziem projektu
id (`npm --prefix frontend run aplikacje:eskarga`, T-037), klient ma `private_key_jwt` i aplikacja potrzebuje klucza prywatnego
`eskarga.key.pem` (oraz `eskarga-workers.key.pem` dla workerów) z katalogu wyników tego narzędzia – wskaż go w `.env`
(`ID_USERS_KLUCZE_DIR`, domyślnie `../../../id/frontend/narzedzia/wyniki/eskarga`). `entrypoint.sh` loguje przy starcie
`Uwierzytelnienie klienta eskarga: private_key_jwt …` albo `client_secret`; `rejestracja.cmd` pokazuje typ ustawiony w Keycloaku.
Błąd `invalid_client: Parameter client_assertion_type is missing` w logu oznacza klienta `client-jwt` bez klucza po stronie eSkargi;
`invalid_client: Invalid token audience` – `aud` asercji inny niż publiczny adres realmu (`ESKARGA_OIDC_ASSERTIONAUDIENCE`,
domyślnie `<ESKARGA_OIDC_ISSUER>/protocol/openid-connect/token`; Keycloak porównuje z adresem frontowym, nie `host.docker.internal`).

Diagnostyka: `infra\windows\logi.cmd app`, filtr `Select-String "oidc|OIDC|bez ról|ELY"` – w dev logger
`org.wildfly.security.http.oidc` ma poziom DEBUG (CLI przy starcie kontenera), więc widać każdy krok mechanizmu.

## Podpis zarządzeń i postanowień na dev (bez YubiKey)

Podpis jest dwuetapowy (ADR 0008, D10): usługa dokumentów liczy skrót (DSS, PAdES), przeglądarka przekazuje go aplikacji lokalnej
na `http://127.0.0.1:17227`, ta podpisuje kluczem sędziego i oddaje wartość podpisu; usługa składa podpis, dodaje znacznik czasu
z TSA dev (B-T) i waliduje wynik – podpis jest przyjęty tylko, gdy walidacja daje TOTAL_PASSED, czyli certyfikat sędziego
i certyfikat TSA muszą być w magazynie zaufania usługi dokumentów. Na dev zamiast YubiKey jest klucz testowy w PKCS#12:

1. Zbuduj projekt (`infra\windows\buduj.cmd`, powstaje `local-signer\target\eskarga-local-signer.jar`) i uruchom środowisko
   (`start.cmd`; kontener `tsa` musi działać – po zmianie obrazu `start.cmd -Przebuduj`).
2. `infra\windows\podpis.cmd` – za pierwszym razem tworzy `infra\docker\.local\podpis\sedzia-test.p12` (klucz RSA, certyfikat
   samopodpisany, dane fikcyjne), pobiera certyfikat TSA z `http://localhost:3180/tsa.crt`, buduje `truststore.p12` i wpisuje
   `ESKARGA_DOCS_TRUSTSTORE=/opt/eskarga/podpis/truststore.p12` oraz hasło do `.env` (katalog `.local` jest montowany do kontenera `app`).
3. Po pierwszym uruchomieniu zrestartuj aplikację: `infra\windows\wdroz.cmd -TylkoApp` (usługa dokumentów czyta magazyn zaufania przy starcie).
4. `infra\windows\podpis.cmd` ponownie – uruchamia aplikację lokalną (`java -jar … --pkcs12 … --origin http://localhost:8082`);
   zostaw okno otwarte. PIN klucza: parametr `-Pin` (domyślnie `123456`), trafia do niej zmienną `ESKARGA_SIGNER_PIN`.
5. W eSkardze: akta sprawy → zakładka **Czynności** → „Podpisz” przy projekcie (projekt powstaje w edytorze S-15 przez „Prześlij
   sędziemu”, np. zarządzenie o brakach z listy kontrolnej). Ekran S-16: krok 2 pobiera certyfikat z aplikacji lokalnej, krok 3 podpisuje.
   Role: sędzia albo Prezes TK; sędzia musi być wyznaczony do sprawy (zespół).

Na prawdziwej stacji: `java -jar eskarga-local-signer.jar --lib <libykcs11> --origin https://<adres eSkargi>` z kluczem w slocie PIV YubiKey,
certyfikat z wewnętrznego CA TK w `ESKARGA_DOCS_TRUSTSTORE`.

## Wdrażanie WAR-ów (dev)

Katalogi `app/target` i `docs-service/target` są montowane do kontenera `app` tylko do odczytu, a `entrypoint.sh` kopiuje z nich
WAR-y do `standalone/deployments/` przy każdym starcie. Po `mvn package` wystarczy `docker compose … run --rm flyway` (nowe migracje) i `docker compose … restart app`
(`infra\windows\wdroz.cmd` robi oba kroki). Pojedyncze pliki nie są montowane celowo: `mvn clean` usuwa WAR w trakcie builda, skaner WildFly
zgłaszał `WFLYDS0008 … FileNotFoundException` i cofał wdrożenie, a po restarcie kontener wciąż widział skasowany plik.

