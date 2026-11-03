# Runbook – aktualizacja Operaton (cykl 6-miesięczny, T-056, architektura §10)

## Zakres
Nowa wersja dystrybucji Operaton dla WildFly (silnik współdzielony) + BOM `operaton-bom` w `pom.xml` + `operaton-external-task-client` w workerach.

## Przed aktualizacją
1. Przeczytaj notatki wydania Operaton (zmiany schematu, usunięte API, wersja WildFly). Lista zgodności WildFly: dystrybucja musi być
   zbudowana dla WildFly 41 (albo decyzja o podniesieniu WildFly – osobny ADR).
2. Zamroź wdrożenia na preprod; zrób kopię bazy: `pg_dump -Fc eskarga > eskarga-przed-operaton-<wersja>.dump`.
3. Uruchom `infra/scripts/test-schema-migration.sh <dump> <nowa-wersja>` – odtwarza kopię w tymczasowej bazie (Docker) i wykonuje
   skrypty `upgrade` Operaton (`sql/upgrade/postgres_engine_<stara>_to_<nowa>.sql`) – sukces skryptu = migracja schematu przechodzi na kopii danych.

## Aktualizacja (preprod → prod)
1. `pom.xml`: `operaton.version` → nowa wersja; `mvn -B verify` (testy procesowe i serwisów na H2), `-Pit` na PostgreSQL.
2. `infra/docker/.env`: `OPERATON_VERSION`; `infra/ansible/group_vars/all/main.yml`: `operaton_version`.
3. `infra/scripts/update-operaton.sh <nowa-wersja>` na węźle aplikacji: zatrzymuje WildFly, pobiera dystrybucję z Nexusa (SHA-256),
   wykonuje skrypty upgrade schematu `operaton` rolą aplikacji (właściciel schematu), kopiuje `standalone/configuration` i `deployments`,
   startuje serwer, sprawdza `/eskarga/health` i wersję silnika w Cockpicie.
4. Workery: nowy JAR z klientem external tasks w tej samej wersji (`systemctl restart eskarga-workers`).
5. Testy dymne: pulpit zadań (S-01), start procesu (założenie sprawy w sprawie testowej na preprod), timer (korekta doręczenia), external task (WireMock).

## Lista kontrolna zgodności WildFly
- [ ] dystrybucja Operaton zbudowana dla WildFly 41 (podsystem `operaton-bpm-platform`)
- [ ] `operaton-engine-cdi` zgodny z CDI 4.1 (Jakarta EE 11)
- [ ] `JakartaEjbProcessApplication` nadal dostępna (albo zmiana `EskargaProcessApplication`)
- [ ] `historyTimeToLive` – zasada egzekwowania bez zmian (ADR: HTTL z konfiguracji przez `@PostDeploy`)
- [ ] REST silnika dla workerów bez zmian łamiących (`fetchAndLock`, `complete`, `handleFailure`)

## Wycofanie
`infra/scripts/update-operaton.sh --rollback <stara-wersja>` przywraca poprzedni katalog dystrybucji; schemat `operaton` z kopii `pg_restore`
(skrypty upgrade Operaton nie mają kroku downgrade – dlatego kopia przed aktualizacją jest obowiązkowa).

## Próbna aktualizacja na środowisku testowym
Do wykonania przez zespół eksploatacji po pierwszym wydaniu produkcyjnym; wynik (czas, problemy) dopisać tutaj.
