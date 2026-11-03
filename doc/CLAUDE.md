# Repertorium Ts/Tw – system repertoryjno-biurowy Trybunału Konstytucyjnego

Ten plik opisuje projekt dla Claude Code. Czytaj go na początku każdej sesji. Listę zadań znajdziesz w `TASKS.md`.

## Co budujemy

System do obsługi skarg konstytucyjnych (repertorium **Ts**) i wniosków podmiotów z art. 191 ust. 1 pkt 3–5 Konstytucji (repertorium **Tw**) na etapie **wstępnej kontroli** (art. 61 ustawy z 30.11.2016 r. o organizacji i trybie postępowania przed TK, Dz.U. 2019 poz. 2393).

System obejmuje:

- rejestrację wpływu pism;
- prowadzenie repertoriów Ts i Tw oraz rejestrów pomocniczych;
- obieg wstępnej kontroli: wyznaczenie sędziego, zarządzenie o brakach, odmowę nadania biegu, zażalenie, nadanie dalszego biegu;
- e-akta z podglądem dokumentów;
- doręczenia i terminy;
- przekazanie sprawy do repertorium SK, K lub U;
- archiwizację.

Interfejs jest wyłącznie po polsku. Użytkownicy to pracownicy TK w sieci wewnętrznej.

Dokumenty źródłowe (traktuj je jako specyfikację):

- Analiza wymagań i przypadków użycia (FR-01…FR-36, NFR-01…NFR-16, UC-01…UC-24, BR-01…BR-10): https://claude.ai/artifact/PbiX9GMeFWy9iSGmPRV4k2
- Architektura rozwiązania (decyzje D1–D13): https://claude.ai/artifact/Urf7aJfJ7myMULA7yvLvvz
- Brief widoku podglądu akt: https://claude.ai/artifact/Y6WjxBhX4nX3o9V4S7sX6z
- Model BPMN wstępnej kontroli: `app/src/main/resources/bpmn/wstepna-kontrola.bpmn` (plik `tk_wstepna_kontrola_Ts_Tw.bpmn` do skopiowania do repozytorium)

Jeżeli zadanie jest sprzeczne z tymi dokumentami, zatrzymaj się i zapytaj. Nie rozstrzygaj tego samodzielnie.

## Stos technologiczny (zatwierdzony 3.11.2026)

| Warstwa | Technologia |
| --- | --- |
| Język | Java 21 (LTS) |
| Serwer | WildFly 41 – dystrybucja Operaton dla WildFly (podsystem silnika współdzielonego) |
| Platforma | Jakarta EE 11: CDI, EJB, JPA, JTA, Jakarta Security, Jakarta REST |
| UI | Jakarta Faces 4.1 + PrimeFaces 15 (classifier `jakarta`), PrimeFaces Extensions; pdf.js tylko w widżecie podglądu akt |
| Silnik procesów | Operaton 2.2 (`operaton-engine-cdi`, aplikacja jako process application EJB) |
| Uwierzytelnianie | ID-users jako dostawca OIDC (Authorization Code + PKCE), aplikacja jako klient poufny |
| Baza | PostgreSQL 17, schematy `repertorium` (migracje Flyway) i `operaton` (skrypty Operaton) |
| Dokumenty | Repozytorium S3 z Object Lock (prod: Ceph RGW – do zakupu; dev: SeaweedFS), EU DSS, OCRmyPDF, veraPDF |
| Pisma z szablonów | Apache FreeMarker 2.3.x (szablony XHTML) → OpenHTMLtoPDF (`io.github.openhtmltopdf`) → PDF/A-2b → veraPDF, w `docs-service` (D13) |
| Wyszukiwanie | OpenSearch (analizator polski) |
| Klucze | HSM przez PKCS#11 (prod: sieciowy HSM – do zakupu; dev: SoftHSM2) |
| Integracje | Workery Java 21 z `operaton-external-task-client` |
| Budowanie | Maven, multi-module |
| Dev/test | Docker Compose |
| Prod | Maszyny wirtualne, konfiguracja Ansible |

Nie wprowadzaj innych frameworków (Spring, Quarkus, Angular, React, Lombok) bez zgody. Jeśli uważasz, że biblioteka jest potrzebna, zaproponuj ją w opisie zmiany.

## Struktura repozytorium (docelowa)

```
/pom.xml                      parent, BOM-y (Operaton 2.2, Jakarta EE 11, PrimeFaces 15)
/app                          WAR: JSF, CDI/EJB, JPA, process application, BPMN/DMN
  src/main/java/pl/ejtechnology/tk/eSkarga/
    web/                      beany JSF (widoki), konwertery, walidatory
    domain/                   encje JPA, usługi domenowe (EJB), reguły BR-xx
    process/                  delegaty CDI, nasłuchy, adapter Operaton
    security/                 OIDC, bieżący użytkownik, uprawnienia do spraw
    documents/                klient repozytorium S3, klient usługi dokumentów
    audit/                    dziennik zdarzeń z łańcuchem skrótów
  src/main/resources/bpmn/    modele BPMN i DMN
  src/main/resources/db/migration/   Flyway (schemat repertorium)
  src/main/webapp/            Facelets (.xhtml), zasoby, widżet pdf.js
/docs-service                 WAR: OCR, PDF/A, DSS, anonimizacja, PKCS#11
/workers                      JAR-y external tasks (doręczenia, poczta, SK, archiwum)
/infra/docker                 compose.yml, obrazy dev/test
/infra/ansible                role dla VM (WildFly, PostgreSQL/Patroni, OpenSearch, HAProxy)
/docs/adr                     decyzje architektoniczne (ADR)
```

Pakiet bazowy: `pl.ejtechnology.tk.eSkarga` (zatwierdzony 3.11.2026). Maven: groupId `pl.ejtechnology.tk`, artifactId z przedrostkiem `eskarga-` (`eskarga-parent`, `eskarga-app`, `eskarga-docs-service`, `eskarga-workers`).

## Polecenia

```bash
mvn -B verify                                   # build + testy jednostkowe i procesowe (JUnit 5, operaton-bpm-assert na H2)
mvn -B verify -Pit                              # testy integracyjne na PostgreSQL 17 (Testcontainers): uprawnienia roli, sygnatury
mvn -B verify -Psecurity                        # skan zależności OWASP Dependency-Check (CVSS ≥ 7 przerywa build)
cp infra/docker/.env.example infra/docker/.env  # uzupełnij hasła przed pierwszym startem
docker compose -f infra/docker/compose.yml up -d --build   # środowisko dev
docker compose -f infra/docker/compose.yml logs -f app
docker compose -f infra/docker/compose.yml --profile monitoring up -d   # Prometheus :9090, Grafana :3000
python3 infra/scripts/er-diagram.py             # diagram ER z encji → docs/model-er.md
cd ui-tests && npm ci && npm test               # Playwright + axe na działającej aplikacji (ESKARGA_URL)
```

Tryb pracy nad interfejsem bez ID-users (tylko stacja deweloperska): `-Deskarga.security.mode=demo -Deskarga.demo.today=2026-11-03`;
warianty ról i stanów ekranów parametrami `?rola=`, `?stan=`, `?widok=` (ADR 0004).

Aktualizuj tę sekcję, gdy dodajesz nowe polecenia.

## Zasady architektoniczne (obowiązkowe)

1. **Jedna transakcja (D1).** Wpis do repertorium, nadanie sygnatury i start lub korelacja procesu Operaton wykonuj w jednej transakcji JTA. Nigdy nie wołaj REST silnika z aplikacji.
2. **Sygnatury bez luk (BR-01).** Używaj licznika `(symbol, rok)` z `SELECT … FOR UPDATE`. Sekwencje bazy danych są zabronione.
3. **Repertorium tylko do dopisywania (NFR-01).** Nie aktualizuj i nie usuwaj wpisów. Korekta tworzy nową wersję z uzasadnieniem i akceptacją (UC-22).
4. **Dziennik zdarzeń (NFR-02).** Każda czynność i każdy odczyt dokumentu trafia do `audit_event` z łańcuchem skrótów (SHA-256 poprzedniego wpisu).
5. **Zmienne procesu bez danych osobowych (D8).** Dozwolone są tylko `caseId`, sygnatura, identyfikator sędziego i daty terminów. Kluczem biznesowym procesu jest sygnatura.
6. **Terminy liczy domena (D6).** Termin wyliczasz w `DeadlineCalculator` (dni wolne, data doręczenia, wstrzymanie z art. 44), a timer BPMN dostaje gotową datę (`${terminBrakow}`). System sygnalizuje termin, ale go nie rozstrzyga.
7. **Uprawnienia na trzech poziomach (D4).** Role ogólne pochodzą z ID-users. Przydział do spraw i upoważnienia („wykonuje obowiązki Prezesa”) są w aplikacji. Każde zapytanie o sprawę lub dokument przechodzi przez `CaseAccessPolicy`.
8. **Tokeny tylko na serwerze.** Przeglądarka dostaje wyłącznie ciasteczko sesji (`HttpOnly`, `Secure`, `SameSite=Strict`).
9. **Integracje tylko przez external tasks (D7).** Kod w WAR nie łączy się z systemami zewnętrznymi. Każda wysyłka ma klucz idempotencji.
10. **Dokumenty.** Pliki są w S3, a w bazie zapisujesz metadane i SHA-256. Do przeglądarki dokumenty wysyłasz przez endpoint z autoryzacją sesji, obsługą HTTP Range i `Cache-Control: no-store`. Dokumenty niejawne nigdy nie trafiają do systemu, zapisujesz tylko ich metadane.
11. **Klucze tylko w HSM.** Kod używa PKCS#11 (SunPKCS11). Nie zapisuj kluczy w plikach ani w bazie.
12. **Podpis dwuetapowy (D10).** Serwer liczy dane do podpisu przez DSS `getDataToSign`. Aplikacja lokalna podpisuje skrót kartą. Serwer składa podpis (`signDocument`), dodaje znacznik czasu i weryfikuje wynik.
13. **Szablony pism w piaskownicy (D13).** FreeMarker działa z ustawieniami: `new_builtin_class_resolver = ALLOWS_NOTHING_RESOLVER` i `api_builtin_enabled = false`, z automatycznym escapowaniem HTML oraz limitem czasu i rozmiaru wyniku. Model danych to płaskie DTO i mapy (nigdy encje JPA ani beany CDI), a daty są już sformatowane po polsku („3 listopada 2026 r.”). Szablony są w bazie z wersjami, a każde pismo zapisuje wersję szablonu, z której powstało. Renderer PDF jest ukryty za interfejsem `PdfRenderer`. Każdy wynik przechodzi veraPDF (PDF/A-2b, osadzone czcionki z polskimi znakami).

## Konwencje kodu

- Kod, nazwy klas, metod i tabel piszesz po angielsku. Terminy domenowe tłumaczysz według słownika poniżej. Teksty UI trzymasz po polsku w `messages_pl.properties`, nigdy w kodzie.
- Daty: `LocalDate` i `Instant`. Strefa czasu `Europe/Warsaw` tylko przy prezentacji i liczeniu terminów. Format UI: `DD.MM.RRRR`.
- Usługi domenowe piszesz jako EJB `@Stateless` z jawnymi granicami transakcji. Beany widoków: `@ViewScoped`.
- Komentarze i Javadoc piszesz po polsku, gdy tłumaczą regułę prawną (podaj artykuł ustawy, np. „art. 61 ust. 3”).
- Każda reguła BR-xx ma test jednostkowy, a każdy przypadek UC-xx test integracyjny lub procesowy.
- Dostępność: komponenty PrimeFaces z etykietami. Status nigdy nie może być przekazany samym kolorem (WCAG 2.1 AA).

### Słownik domenowy PL → EN

| PL | EN (w kodzie) |
| --- | --- |
| sprawa | `CaseFile` |
| repertorium | `Docket` |
| sygnatura | `DocketNumber` |
| pismo wpływające / dziennik wpływu | `IncomingLetter` / `IncomingRegister` |
| skarżący / wnioskodawca / pełnomocnik | `Complainant` / `Applicant` / `Attorney` |
| przedmiot kontroli / wzorzec kontroli | `ReviewSubject` / `ReviewStandard` |
| orzeczenie ostateczne | `FinalRuling` |
| czynność (zarządzenie, postanowienie) | `ProceduralAct` (`Order`, `Decision`) |
| doręczenie | `Delivery` |
| termin | `Deadline` |
| zażalenie | `Appeal` |
| upoważnienie | `Delegation` |
| wstępna kontrola | `PreliminaryReview` |
| nadanie dalszego biegu / odmowa | `AdmitForReview` / `RefuseToAdmit` |
| e-akta / karta akt | `CaseRecord` / `RecordPage` |

## Testy

- Jednostkowe: JUnit 5 i AssertJ.
- Procesowe: `operaton-bpm-assert` dla każdej ścieżki modelu (nadanie biegu, braki uzupełnione, braki nieuzupełnione, odmowa bez zażalenia, zażalenie spóźnione, zażalenie uwzględnione i nieuwzględnione).
- Integracyjne: Testcontainers (PostgreSQL, SeaweedFS, mock OIDC).
- UI: Playwright dla ekranów krytycznych (rejestracja wpływu, podgląd akt, podpis).
- Zadanie jest skończone dopiero przy zielonym `mvn -B verify`.

## Dane testowe

Używaj wyłącznie danych fikcyjnych, np. „Jan Przykładowy”, „adw. Anna Testowa”, „sędzia TK Piotr Fikcyjny”, sygnatura Ts 312/26. Nigdy nie wstawiaj prawdziwych nazwisk, sygnatur ani numerów PESEL.

## Bezpieczeństwo pracy

- Nie commituj sekretów. Konfiguracja przychodzi przez zmienne środowiskowe lub system properties WildFly, a w dev przez `.env` (w `.gitignore`).
- Nie wyłączaj walidacji TLS ani weryfikacji podpisu tokenu, nawet w testach. Używaj mock OIDC z własnymi kluczami.
- Kontrakt ID-users nie jest jeszcze znany. Adres issuera, nazwy claimów ról i grup oraz claim MFA (`acr`/`amr`) są konfigurowalne. Nie zgaduj ich na sztywno.

## Sposób pracy

1. Bierz z `TASKS.md` jedno zadanie naraz, w kolejności i z uwzględnieniem zależności.
2. Przed kodem opisz krótko plan. Przy zadaniach oznaczonych **[decyzja]** najpierw zapytaj.
3. Po skończeniu odhacz zadanie w `TASKS.md` i dopisz krótką notatkę: co zrobiono i co zostało.
4. Nowe ustalenia zapisuj jako ADR w `docs/adr/NNNN-tytul.md`.

## Otwarte kwestie (nie implementuj na sztywno)

- Kontrakt OIDC ID-users (endpointy, claimy ról i grup, MFA, API użytkowników, instancja testowa).
- Aplikacja lokalna do podpisu: SDK dostawcy kart czy własna. Do tego czasu używaj interfejsu `LocalSignerClient` z implementacją testową.
- Produkty repozytorium S3 i HSM (zakup). Kod pisz pod API S3 i PKCS#11, bez zależności od dostawcy.
- API e-Doręczeń, operatora pocztowego i repertoriów SK/K/U. Do tego czasu twórz tylko interfejsy i zaślepki WireMock.
- Kategorie archiwalne i okresy retencji. Trzymaj je w konfiguracji.
