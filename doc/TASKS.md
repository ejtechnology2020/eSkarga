# Lista zadań – Repertorium Ts/Tw

Realizuj zadania po kolei, z uwzględnieniem zależności. Zasady pracy są w `CLAUDE.md`. Oznaczenia FR, NFR, UC i BR odsyłają do analizy wymagań, a D do architektury.

Etykiety: **[decyzja]** – zanim zaczniesz, zapytaj człowieka; **[zaślepka]** – kontrakt zewnętrzny nieznany, zbuduj interfejs i implementację testową.

## Przegląd

| Etap | Zadania | Wynik etapu |
| --- | --- | --- |
| 0. Fundament | T-001 – T-006 | Pusty system uruchamia się w Dockerze, logowanie OIDC działa, CI zielone |
| 1. Rdzeń repertorium | T-010 – T-016 | Rejestracja wpływu, sprawy Ts/Tw z sygnaturą, dziennik zdarzeń, uprawnienia |
| 2. Proces wstępnej kontroli | T-020 – T-028 | Pełny obieg Ts/Tw w Operaton z terminami i pismami |
| 3. Dokumenty i podgląd akt | T-030 – T-037 | E-akta w S3, OCR/PDF/A, wyszukiwanie, ekran podglądu akt |
| 4. Podpis i doręczenia | T-040 – T-043 | Podpis kartą, rejestr doręczeń, workery integracji |
| 5. Raporty, archiwum, eksploatacja | T-050 – T-056 | Raporty, archiwizacja, Ansible dla VM, monitoring, utwardzenie |

---

## Etap 0. Fundament

### [x] T-001 Szkielet repozytorium i budowania
- **Cel:** projekt Maven multi-module zgodny ze strukturą z `CLAUDE.md`.
- **Zakres:**
    - parent `pom.xml` z Java 21, BOM-ami Operaton 2.2, Jakarta EE 11 i PrimeFaces 15 (`jakarta`);
    - moduły `app` (WAR), `docs-service` (WAR), `workers` (JAR);
    - Maven Enforcer (wersja Javy, zakaz duplikatów zależności), formatowanie kodu, `.editorconfig`, `.gitignore`;
    - szkielet `docs/adr/0001-stos-technologiczny.md` z decyzjami D1–D13.
- **Kryteria akceptacji:**
    - [x] `mvn -B verify` przechodzi na pustym projekcie;
    - [x] WAR-y budują się bez ostrzeżeń o konfliktach wersji.
- **Zależności:** brak. **Odwołania:** D2, D9.
- **Ustalone:** pakiet bazowy `pl.ejtechnology.tk.eSkarga`, groupId `pl.ejtechnology.tk`, artifactId `eskarga-*` (3.11.2026).

### [~] T-002 Środowisko dev w Docker Compose
- **Cel:** jedno polecenie uruchamia całe środowisko deweloperskie.
- **Zakres** (`infra/docker/compose.yml`):
    - obraz dystrybucji Operaton dla WildFly 41 z montowanym katalogiem `deployments/`;
    - PostgreSQL 17, SeaweedFS (S3), OpenSearch, SoftHSM2;
    - mock OIDC zastępujący ID-users (np. `mock-oauth2-server`) z konfigurowalnymi claimami ról i `acr`;
    - WireMock dla systemów zewnętrznych;
    - plik `.env.example`.
- **Kryteria akceptacji:**
    - [ ] `docker compose up -d` startuje wszystkie usługi z health checkami;
    - [ ] Cockpit Operaton odpowiada na porcie dev;
    - [ ] README opisuje porty i loginy testowe.
- **Zależności:** T-001. **Odwołania:** D11.

### [~] T-003 Baza danych i migracje
- **Cel:** schematy `repertorium` i `operaton` w jednej bazie, jedno źródło danych w WildFly.
- **Zakres:**
    - definicja datasource w CLI WildFly (skrypt `.cli` w repozytorium);
    - Flyway dla schematu `repertorium` (V1: tabele techniczne, `audit_event`, `docket_counter`);
    - schemat Operaton ze skryptów dystrybucji;
    - użytkownik aplikacji bez uprawnień UPDATE/DELETE na tabelach repertorium (rola osobna dla migracji).
- **Kryteria akceptacji:**
    - [x] migracje uruchamiają się przed startem aplikacji w dev (usługa `flyway` w Compose, ADR 0016);
    - [~] test integracyjny `PostgresPrivilegesIT` (Testcontainers, `-Pit`) napisany – do uruchomienia z Dockerem.
- **Zależności:** T-002. **Odwołania:** D1, NFR-01.

### [~] T-004 Logowanie OIDC przez ID-users
- **Cel:** użytkownik loguje się przez ID-users, a aplikacja zna jego tożsamość, role i poziom uwierzytelnienia.
- **Zakres:**
    - `@OpenIdAuthenticationMechanismDefinition` (albo `elytron-oidc-client`, uzasadnij w ADR), Authorization Code + PKCE, klient poufny;
    - konfigurowalne: issuer, client id/secret, claim ról, claim grup, wymagana wartość `acr`/`amr`;
    - mapowanie ról ID-users na role aplikacji `TK_REFERENT`, `TK_ASYSTENT`, `TK_SEDZIA`, `TK_PREZES`, `TK_KIEROWNIK`, `TK_BIURO_PODAWCZE`, `TK_ARCHIWISTA`, `TK_ADMIN`;
    - odmowa wejścia bez MFA;
    - ciasteczko sesji `HttpOnly`, `Secure`, `SameSite=Strict`; wylogowanie lokalne i back-channel logout;
    - bean `CurrentUser` (CDI);
    - interceptor ustawiający `identityService.setAuthentication(userId, groups)` w Operaton.
- **Kryteria akceptacji:**
    - [ ] logowanie przez mock OIDC w dev;
    - [~] `MfaFilter` + `MfaAndLogoutTokenTest` (polityka claimów); test na mock OIDC po uruchomieniu środowiska;
    - [~] test Playwright `ui-tests/tests/tokeny.spec.ts` napisany (wymaga mock OIDC);
    - [x] ADR 0002 (uzupełnienie: MFA, back-channel logout, role techniczne).
- **Zależności:** T-002. **Odwołania:** D3, NFR-04. **[zaślepka]** kontrakt ID-users.

### [~] T-005 Szablon interfejsu JSF
- **Cel:** wspólny layout, nawigacja i komunikaty dla wszystkich ekranów.
- **Zakres:**
    - szablon Facelets (nagłówek z użytkownikiem i rolą, menu zależne od roli, obszar treści);
    - motyw PrimeFaces stonowany (urzędowy);
    - `messages_pl.properties`, strony błędów 403, 404 i 500 bez szczegółów technicznych;
    - nagłówki bezpieczeństwa (CSP, HSTS, X-Frame-Options, Referrer-Policy) przez filtr.
- **Kryteria akceptacji:**
    - [~] ekran startowy działa po zalogowaniu (pulpit gotowy; uruchomienie na WildFly do potwierdzenia, brak serwera w sesji);
    - [~] audyt axe-core: `ui-tests/tests/a11y.spec.ts` (ADR 0020) – do uruchomienia na działającej aplikacji;
    - [x] CSP bez `unsafe-eval` (`primefaces.CSP=true`, polityka w `web.xml`).
- **Zależności:** T-004. **Odwołania:** NFR-10.

### [~] T-006 Potok CI
- **Cel:** każda zmiana jest automatycznie budowana i testowana.
- **Zakres:**
    - build, testy jednostkowe i procesowe, testy integracyjne (Testcontainers);
    - skan zależności (OWASP Dependency-Check lub równoważny);
    - artefakty WAR/JAR z sumami kontrolnymi.
- **Kryteria akceptacji:**
    - [~] `.github/workflows/ci.yml` (build, `-Pit`, `-Psecurity`, artefakty z SHA256SUMS) – pierwszy przebieg po włączeniu Actions (ADR 0005);
    - [x] Dependency-Check z progiem CVSS ≥ 7 (profil `security`, klucz NVD w sekretach CI).
- **Zależności:** T-001. **Odwołania:** NFR-13.
- **[decyzja]** platforma CI (GitLab CI, Jenkins, GitHub Actions).

---

## Etap 1. Rdzeń repertorium

### [~] T-010 Model domeny (JPA)
- **Cel:** encje z sekcji 8.1 analizy wymagań.
- **Zakres:**
    - encje `IncomingLetter`, `CaseFile`, `Party`, `Attorney`/`PowerOfAttorney`, `ReviewSubject`, `FinalRuling`, `ProceduralAct`, `Document`, `Delivery`, `Deadline`, `Appeal`, `AccessRequest`, `Delegation`;
    - migracje Flyway, wersjonowanie wpisów zamiast UPDATE;
    - słowniki: sądy, organy, typy pism, dni wolne.
- **Kryteria akceptacji:**
    - [~] encje + migracje V1–V5 (`ModelSchemaTest` na H2 z walidacją schematu); `DocketConcurrencyPostgresIT` na Testcontainers napisany;
    - [x] diagram ER `docs/model-er.md` (generator `infra/scripts/er-diagram.py`).
- **Zależności:** T-003. **Odwołania:** analiza §8.1.

### [x] T-011 Dziennik wpływu i rejestracja pisma (UC-01)
- **Cel:** biuro podawcze rejestruje pismo w mniej niż 3 minuty.
- **Zakres:**
    - formularz: kanał, data wpływu, data wniesienia (stempel lub UPO, z flagą „niepewna”), nadawca, liczba załączników i odpisów;
    - upload skanów;
    - numer dziennika, etykieta z kodem kreskowym (PDF), kolejka „Do kwalifikacji”;
    - wariant dla dokumentu niejawnego: zapis wyłącznie metadanych.
- **Kryteria akceptacji:**
    - [x] `IncomingRegisterTest` (numeracja, pismo elektroniczne bez UPO, dokument niejawny – tylko metadane);
    - [x] zdarzenie w dzienniku audytu.
- **Zależności:** T-010, T-015. **Odwołania:** FR-01 – FR-03, UC-01.

### [x] T-012 Generator sygnatur (BR-01)
- **Cel:** sygnatury Ts n/rr i Tw n/rr bez luk i duplikatów.
- **Zakres:**
    - `DocketNumberService`: tabela `docket_counter(symbol, year, last_value)` z `SELECT … FOR UPDATE` w transakcji wywołującego;
    - reset numeracji co rok.
- **Kryteria akceptacji:**
    - [x] test współbieżności: 50 równoległych transakcji, w tym 10 wycofanych, daje numery 1…40 bez luk (`DocketNumberAllocatorTest`, repozytorium w pamięci; wersja na PostgreSQL/Testcontainers do dodania);
    - [x] numer nigdy nie jest używany ponownie.
- **Zależności:** T-010. **Odwołania:** BR-01, FR-06.

### [x] T-013 Kwalifikacja pisma i założenie sprawy (UC-02, UC-03, UC-04)
- **Cel:** referent zamienia pismo w sprawę Ts/Tw albo dołącza je do sprawy w toku.
- **Zakres:**
    - kolejka pism do kwalifikacji, wybór typu (Ts, Tw, pismo do sprawy, pismo ogólne);
    - podpowiedzi powiązań (ten sam skarżący, pełnomocnik, przepis);
    - karta sprawy (strony, przedmiot, wzorce, orzeczenie ostateczne);
    - zatwierdzenie w jednej transakcji: nadanie sygnatury (T-012), wpis, start procesu Operaton (kluczem biznesowym jest sygnatura).
- **Kryteria akceptacji:**
    - [x] `CaseFileServiceTest.bladStartuProcesuWycofujeWpisINieZuzywaNumeru`;
    - [x] `attachLetter` koreluje komunikat uzupełnienia (gdy proces czeka), zażalenie przez `AppealService`.
- **Zależności:** T-012, T-020. **Odwołania:** FR-04 – FR-07, D1.

### [~] T-014 Korekta wpisu w repertorium (UC-22)
- **Cel:** korekta bez nadpisywania historii.
- **Zakres:** wniosek referenta z uzasadnieniem, akceptacja kierownika, nowa wersja wpisu, podgląd historii wersji.
- **Kryteria akceptacji:**
    - [x] decyzje korekt jako osobne wiersze, rola aplikacji bez UPDATE/DELETE (V5, V5.1, ADR 0016);
    - [~] historia wersji w `KorektaBean.getHistoria()` – widok S-09 do rozbudowy o tabelę wersji.
- **Zależności:** T-013. **Odwołania:** FR-08, NFR-01.

### [~] T-015 Dziennik zdarzeń z łańcuchem skrótów
- **Cel:** niezaprzeczalny ślad każdej czynności i każdego odczytu dokumentu.
- **Zakres:**
    - `AuditService` (interceptor CDI i wywołania jawne);
    - pola: kto, rola, co, obiekt, sprawa, czas, adres, skrót poprzedniego wpisu i własny skrót;
    - wysyłka do SIEM (syslog JSON);
    - polecenie weryfikacji łańcucha.
- **Kryteria akceptacji:**
    - [x] test: modyfikacja wpisu w bazie jest wykrywana przez weryfikator (`AuditChainTest`);
    - [~] każda usługa domenowa zapisuje zdarzenia (w tym odmowy w REQUIRES_NEW, ADR 0014); wysyłka syslog do SIEM i podpis łańcucha kluczem HSM (`/podpis/audyt`) do spięcia harmonogramem.
- **Zależności:** T-003. **Odwołania:** NFR-02.

### [x] T-016 Uprawnienia do spraw i upoważnienia
- **Cel:** kontrola dostępu na poziomie sprawy oraz czynności „za Prezesa”.
- **Zakres:**
    - `CaseAccessPolicy` (przydział sędziego, zespołu, referenta; role ogólne);
    - `Delegation` (osoba, funkcja, od–do, akt podstawowy);
    - weryfikacja upoważnienia w dacie czynności (BR-10);
    - ekran administracji upoważnieniami;
    - raport przeglądu uprawnień.
- **Kryteria akceptacji:**
    - [x] testy: asystent nie widzi notatek spraw innego sędziego (`CaseAccessPolicyTest`);
    - [x] czynność Prezesa bez ważnego upoważnienia → odmowa i zdarzenie audytu (`JudgeAssignmentServiceTest`, `DelegationService.requireActingAsPresident`).
- **Zależności:** T-004, T-010. **Odwołania:** D4, BR-10, FR-36.

---

## Etap 2. Proces wstępnej kontroli

### [~] T-020 Model BPMN w aplikacji
- **Cel:** dostosowany model `wstepna-kontrola.bpmn` wdrażany przez process application.
- **Zakres:**
    - skopiuj `tk_wstepna_kontrola_Ts_Tw.bpmn`;
    - ustaw `isExecutable="true"`, `historyTimeToLive` z konfiguracji;
    - grupy kandydatów (`tk-referenci`, `tk-prezes`, `tk-sklad`) i `assignee` sędziego z przydziału;
    - timery z daty domenowej (`${terminBrakow}`, `${terminZazalenia}`) zamiast stałego `P7D`;
    - korelacja komunikatów `Msg_Uzupelnienie` i `Msg_Zazalenie` po kluczu biznesowym;
    - external tasks dla doręczeń i przekazania;
    - `processes.xml`, klasa `@ProcessApplication` (EJB);
    - tylko identyfikatory w zmiennych procesu.
- **Kryteria akceptacji:**
    - [~] model (z external tasks doręczeń i nasłuchem przekazania) przechodzi testy na silniku H2; HTTL z konfiguracji (`@PostDeploy`); wdrożenie na WildFly do potwierdzenia;
    - [x] żadna zmienna procesu nie zawiera danych osobowych (`ProcessVariablesTest`, `WstepnaKontrolaProcessTest`).
- **Zależności:** T-002, T-004. **Odwołania:** D1, D6, D8, D9.

### [x] T-021 Testy procesowe
- **Cel:** każda ścieżka modelu ma test.
- **Zakres** (`operaton-bpm-assert`):
    - nadanie biegu od razu;
    - braki uzupełnione;
    - braki nieuzupełnione (timer) → odmowa;
    - odmowa bez zażalenia;
    - zażalenie spóźnione;
    - zażalenie nieuwzględnione;
    - zażalenie uwzględnione → nadanie biegu;
    - pismo ogólne.
- **Kryteria akceptacji:**
    - [x] 100% przejść (sequence flow) pokrytych (8 testów: pismo ogólne, nadanie biegu, braki uzupełnione, braki nieuzupełnione → odmowa prawomocna, zażalenie spóźnione, nieuwzględnione, uwzględnione, zmienne bez danych osobowych);
    - [~] testy w CI – workflow gotowy (ADR 0005).
- **Zależności:** T-020. **Odwołania:** UC-07, UC-10 – UC-12.

### [x] T-022 Pulpit zadań (UC-18)
- **Cel:** użytkownik widzi swoje zadania i terminy w jednym miejscu.
- **Zakres:**
    - lista zadań z `TaskService` filtrowana przez `CaseAccessPolicy`;
    - kolumny: sygnatura, zadanie, termin, liczba dni;
    - sortowanie po terminie;
    - widok kierownika: zaległości i obciążenie.
- **Kryteria akceptacji:**
    - [x] `TaskBoardService.Item.nearDeadline()` → `DeadlineView` WARNING (tekst „za 2 dni” + kolor);
    - [x] pulpit na `TaskService` + `CaseAccessPolicy` (`TaskBoardAndTransferTest`).
- **Zależności:** T-020, T-016. **Odwołania:** FR-33, UC-18.

### [x] T-023 Wyznaczenie sędziego (UC-05)
- **Cel:** Prezes lub osoba upoważniona wyznacza sędziego do wstępnej kontroli.
- **Zakres:** zadanie Prezesa z podglądem obciążenia sędziów, zarządzenie o wyznaczeniu, przydział do sprawy, sprawdzenie upoważnienia.
- **Kryteria akceptacji:**
    - [x] zarządzenie w aktach (`CaseDocument` + `ProceduralAct`);
    - [x] sędzia staje się `assignee` zadania kontroli (`JudgeAssignmentServiceTest`);
    - [x] zdarzenie audytu.
- **Zależności:** T-016, T-020. **Odwołania:** FR-14, UC-05.

### [x] T-024 Lista kontrolna wymogów formalnych (DMN, UC-06)
- **Cel:** lista wymogów dobierana do typu sprawy bez zmian w kodzie.
- **Zakres:**
    - tabela DMN `wymogi-formalne` (skarga: art. 53; wniosek: art. 47–49);
    - ekran listy kontrolnej (spełniony / brak / do sprawdzenia, komentarz);
    - weryfikacja pełnomocnika i wyjątków zawodowych (BR-05).
- **Kryteria akceptacji:**
    - [x] tabela `wymogi-formalne.dmn` wdrażana osobno (process application / Cockpit), kod czyta wynik przez `DecisionService` (ADR 0015);
    - [x] `FormalRequirementsTest` (skarga z/bez pełnomocnika, wyjątki zawodowe BR-05, wniosek).
- **Zależności:** T-020. **Odwołania:** FR-11, FR-13, BR-05.

### [x] T-025 Kalkulator terminów (BR-02 – BR-04)
- **Cel:** poprawne wyliczanie terminów 7-dniowych i 3-miesięcznych.
- **Zakres:**
    - `DeadlineCalculator`: początek od doręczenia, dni wolne z konfigurowalnego kalendarza, przesunięcie na dzień roboczy;
    - wstrzymanie na czas wniosku o pełnomocnika z urzędu;
    - data nadania pocztowego lub UPO jako data wniesienia;
    - aktualizacja zmiennej i timera przy korekcie doręczenia.
- **Kryteria akceptacji:**
    - [x] testy parametryczne na ≥ 30 przypadkach (`DeadlineCalculatorTest`: 40 przypadków – święta ruchome, koniec miesiąca, rok przestępny, wstrzymanie);
    - [x] w UI termin opisany jako „wyliczenie pomocnicze” (wskaźnik `aux`, S-07, S-14, S-18).
- **Zależności:** T-010. **Odwołania:** D6, FR-12, FR-17.
- **[decyzja]** zasady liczenia (dni wolne, doręczenie zastępcze) – do potwierdzenia przez Biuro Służby Prawnej.

### [~] T-026 Pisma z szablonów – FreeMarker + OpenHTMLtoPDF (D13)
- **Cel:** projekty pism generowane z danych sprawy jako PDF/A-2b.
- **Zakres:**
    - w `docs-service`: `TemplateRenderer` (Apache FreeMarker 2.3.x, wersja z BOM-u w parent pom) i `PdfRenderer` (OpenHTMLtoPDF, `io.github.openhtmltopdf`, tryb PDF/A-2b) za interfejsami;
    - konfiguracja piaskownicy FreeMarker zgodnie z zasadą 13 w `CLAUDE.md`, z testem bezpieczeństwa: szablon próbujący `?new`, `?api` albo wywołań klas Java kończy się błędem;
    - płaskie DTO danych pisma budowane w domenie (`LetterModel`), daty sformatowane po polsku, bez encji JPA;
    - szablony XHTML z CSS do druku (`@page`, nagłówek, numeracja stron): zarządzenie o usunięciu braków, postanowienie o nadaniu dalszego biegu, postanowienie o odmowie (wybór podstawy z art. 61 ust. 4 pkt 1–3), postanowienie o pozostawieniu zażalenia bez rozpoznania, zawiadomienia (art. 63), odpowiedź na pismo ogólne;
    - tabela `letter_template` z wersjami (projekt, aktywna, wycofana), autorem i datą; pismo zapisuje identyfikator wersji szablonu;
    - osadzone czcionki z polskimi znakami (w dev Liberation Serif);
    - walidacja wyniku veraPDF przed zapisem w repozytorium;
    - wersjonowanie projektów pism i przesłanie do podpisu (T-040).
- **Kryteria akceptacji:**
    - [x] 8 szablonów → PDF/A-2b przez veraPDF (`VeraPdfTemplatesTest`);
    - [x] braki z listy kontrolnej w modelu pisma (`EdytorBean.sendToJudge` → `LetterService.draft`);
    - [x] testy piaskownicy i testy na znakach diakrytycznych przechodzą (`FreemarkerSandboxTest`);
    - [ ] testy wizualne: porównanie z wzorcem – po zatwierdzeniu układu pism przez TK;
    - [~] czas render + veraPDF mierzony w teście (limit 10 s w CI; na stacji < 2 s) – pomiar na docelowym serwerze.
- **Zależności:** T-024, T-031. **Odwołania:** FR-15, FR-16, D13.

### [~] T-026a Edytor szablonów (S-24)
- **Cel:** administrator merytoryczny zarządza szablonami bez wdrożenia.
- **Zakres:**
    - ekran listy szablonów z wersjami;
    - edytor kodu szablonu z podświetlaniem składni;
    - podgląd PDF na danych testowych (sprawa Ts 312/26 z danych fikcyjnych);
    - walidacja przed aktywacją (kompilacja szablonu, render, veraPDF);
    - aktywacja z akceptacją drugiej osoby;
    - historia zmian w dzienniku zdarzeń.
- **Kryteria akceptacji:**
    - [x] walidacja przez usługę dokumentów blokuje aktywację (`TemplateService.activate`, `LettersResourceTest`);
    - [x] aktywacja w dzienniku zdarzeń;
    - [x] pismo pamięta wersję szablonu (`CaseDocument.templateVersion`).
- **Zależności:** T-026, T-015. **Odwołania:** FR-35, NFR-15, D13.

### [~] T-027 Zażalenie (UC-11)
- **Cel:** rejestracja zażalenia, kontrola terminu, skierowanie do składu.
- **Zakres:**
    - rejestr zażaleń, ocena terminowości (wynik pomocniczy);
    - zadanie Prezesa: skład 3 sędziów i termin posiedzenia;
    - wynik składu;
    - blokada ponownego zażalenia (BR-07).
- **Kryteria akceptacji:**
    - [~] `AppealService` (rejestracja, BR-07, skierowanie BR-10, wynik) z testami; ekran S-11 do spięcia z akcjami;
    - [x] sygnatura Ts/Tw bez zmian (`TaskBoardAndTransferTest`).
- **Zależności:** T-023, T-025. **Odwołania:** FR-18, UC-11.

### [x] T-028 Nadanie biegu i przekazanie (UC-12)
- **Cel:** przekazanie sprawy do repertorium SK, K lub U.
- **Zakres:**
    - external task `przekazanie-merytoryczne` z kluczem idempotencji;
    - powiązanie sygnatur Ts ↔ SK;
    - sprawa Ts/Tw tylko do odczytu z banerem;
    - zawiadomienia (art. 63).
- **Kryteria akceptacji:**
    - [x] `MeritTransferHandlerTest` (Idempotency-Key, 500 → 201 bez duplikatu);
    - [x] `TransferCompletedListener` → `markTransferred` → status tylko do odczytu (BR-08).
- **Zależności:** T-042. **Odwołania:** FR-19, BR-08. **[zaślepka]** API repertoriów SK/K/U.

---

## Etap 3. Dokumenty i podgląd akt

### [~] T-030 Repozytorium dokumentów (S3)
- **Cel:** bezpieczny zapis i odczyt plików akt.
- **Zakres:**
    - `DocumentStore` na AWS SDK v2 (API S3, bez zależności od dostawcy);
    - Object Lock (compliance) dla dokumentów zamkniętych, wersjonowanie, SSE;
    - SHA-256 w bazie i weryfikacja przy odczycie.
- **Kryteria akceptacji:**
    - [ ] testy na SeaweedFS (Testcontainers, profil `-Pit`) – do dodania; `InMemoryDocumentStore` w testach serwisów;
    - [x] `DocumentServlet`: niezgodny SHA-256 → 409 i zdarzenie odmowy.
- **Zależności:** T-002, T-010. **Odwołania:** D5, D12.

### [~] T-031 Usługa dokumentów
- **Cel:** przetwarzanie dokumentów poza aplikacją główną.
- **Zakres** (`docs-service`):
    - OCR (OCRmyPDF, język polski), konwersja i walidacja PDF/A-2b (veraPDF);
    - walidacja podpisów EU DSS (PAdES/XAdES/CAdES) z raportem;
    - wywołania jako external tasks (`dokument-przetworz`) z ponawianiem.
- **Kryteria akceptacji:**
    - [~] OCRmyPDF przez CLI (zaślepka, gdy brak binarki); tekst trafia do indeksu wyszukiwania;
    - [x] raport DSS w `CaseDocument.signatureReport` (`PadesSigningTest`, `applyProcessing`);
    - [x] `SignatureReport.warning()` / status `NIEZWERYFIKOWANY`.
- **Zależności:** T-030. **Odwołania:** NFR-11.

### [~] T-032 Wyszukiwanie (UC-20)
- **Cel:** wyszukiwanie spraw i dokumentów.
- **Zakres:**
    - indeks OpenSearch z analizatorem polskim (tekst OCR, metadane, przepisy);
    - filtrowanie wyników przez `CaseAccessPolicy` w aplikacji;
    - ekran wyszukiwania;
    - odbudowa indeksu z bazy.
- **Kryteria akceptacji:**
    - [~] indeks w pamięci natychmiastowy; pomiar na OpenSearch z `analysis-stempel` po uruchomieniu (ADR 0018);
    - [x] `SearchServiceTest` (asystent poza zespołem, administrator – brak wyników).
- **Zależności:** T-031, T-016. **Odwołania:** FR-31, NFR-08.

### [~] T-033 Ekran podglądu akt – układ i dane
- **Cel:** ekran zgodny z briefem (sekcje 3–5).
- **Zakres:**
    - stały nagłówek sprawy (sygnatura, status, biegnący termin, flagi, akcje);
    - `p:splitter` z trzema kolumnami;
    - spis akt (tomy, karty, chronologia, zakładki Akta jawne / Notatki wewnętrzne);
    - panel boczny (Terminy, Czynności, Lista kontrolna, Metadane);
    - warianty ról i statusów;
    - dokument niejawny jako plansza bez podglądu.
- **Kryteria akceptacji:**
    - [~] sygnatura, status i termin widoczne bez przewijania w 1366×768 (układ z makiety R14; do sprawdzenia w przeglądarce);
    - [x] warianty z §5 briefu (`?widok=asystent|referent|anonim|zakonczona|lista|dzielony`, statusy z `CaseSummary`);
    - [ ] axe-core bez błędów krytycznych (wymaga uruchomionej aplikacji).
- **Zależności:** T-022, T-030. **Odwołania:** brief, FR-23.

### [~] T-034 Widżet PDF (pdf.js)
- **Cel:** podgląd dokumentu z widokiem dzielonym, bez łamania layoutu JSF.
- **Zakres:**
    - komponent kompozytowy JSF z `pdfjs-dist` (worker z własnej domeny, `isEvalSupported: false`);
    - endpoint `/akta/{caseId}/dokument/{docId}` z autoryzacją sesji, HTTP Range i `no-store`;
    - zoom, numer karty i strony, wyszukiwanie;
    - widok dzielony (dwie instancje);
    - skróty klawiszowe z briefu §6;
    - znak wodny przy pobraniu.
- **Kryteria akceptacji:**
    - [x] pdf.js z `disableAutoFetch` + HTTP Range w `DocumentServlet`, strony renderowane leniwie;
    - [x] widok dzielony (dwie instancje `tk:pdfViewer`);
    - [~] `ui-tests/tests/akta.spec.ts` – do uruchomienia na działającej aplikacji.
- **Zależności:** T-033. **Odwołania:** brief §4.3, §6.
- **[decyzja]** czy `pe:documentViewer` wystarcza (najpierw zrób krótki prototyp porównawczy).

### [x] T-035 Notatki wewnętrzne i zaznaczenia
- **Cel:** praca zespołu sędziego bez mieszania z aktami jawnymi.
- **Zakres:**
    - notatki do sprawy;
    - zaznaczenia jako JSON ze współrzędnymi, rysowane jako nakładka;
    - widoczność tylko dla zespołu sędziego;
    - kopiowanie z cytatem (sygnatura, karta).
- **Kryteria akceptacji:**
    - [x] `ArchiveService.buildPackage` pomija notatki, `recordPages` filtruje poza zespołem, `NoteService` odmawia (`AccessNotesAndPublicationTest`, `ArchiveReportTemplateTest`).
- **Zależności:** T-034. **Odwołania:** FR-24.

### [x] T-036 Anonimizacja i wgląd osób trzecich (UC-15, UC-16)
- **Cel:** zanonimizowane wersje dokumentów i wgląd w akta.
- **Zakres:**
    - generowanie zanonimizowanej kopii (redakcja po stronie serwera, z kontrolą człowieka);
    - flaga art. 80;
    - rejestr wniosków o wgląd;
    - widok osoby trzeciej z banerem.
- **Kryteria akceptacji:**
    - [x] `visibleFileKeys` zwraca tylko wersje zanonimizowane (`AccessNotesAndPublicationTest`);
    - [x] `Publication.approve` odmawia bez wersji zanonimizowanej.
- **Zależności:** T-031, T-033. **Odwołania:** FR-22, FR-27, FR-29.

### [~] T-037 Integracja z HSM (PKCS#11)
- **Cel:** klucze wyłącznie w HSM.
- **Zakres:**
    - konfiguracja SunPKCS11 (SoftHSM2 w dev);
    - opakowywanie kluczy plików;
    - odszyfrowanie wpływających PDF-ów zaszyfrowanych certyfikatem (Adobe.PubSec) w usłudze dokumentów;
    - podpis łańcucha audytu.
- **Kryteria akceptacji:**
    - [~] skan repozytorium `RepositorySecretsScanTest`; skan bazy – zapytanie kontrolne w przeglądzie uprawnień;
    - [~] `HsmKeyStore` (SunPKCS11) z testami w trybie PKCS#12 (zabroniony w PROD); SoftHSM2 w Compose/Ansible – test po uruchomieniu.
- **Zależności:** T-030, T-015. **Odwołania:** D12.

---

## Etap 4. Podpis i doręczenia

### [~] T-040 Podpis kwalifikowany dwuetapowy (D10)
- **Cel:** sędzia podpisuje kartą przez aplikację lokalną.
- **Zakres:**
    - serwer: DSS `getDataToSign` → interfejs `LocalSignerClient` (przeglądarka ↔ aplikacja lokalna) → `signDocument` + znacznik czasu (PAdES-B-T) → walidacja;
    - kolejne podpisy w składzie 3 sędziów;
    - implementacja testowa podpisująca certyfikatem z SoftHSM2.
- **Kryteria akceptacji:**
    - [x] `PadesSigningTest` (TOTAL_PASSED z kotwicą zaufania, manipulacja → błąd);
    - [x] `SigningService.complete` (`LetterAndSigningServiceTest`).
- **Zależności:** T-026, T-037. **Odwołania:** FR-16, D10.
- **[decyzja]** aplikacja lokalna: SDK dostawcy kart czy własna. **[zaślepka]** do tego czasu.

### [x] T-041 Rejestr doręczeń (UC-08)
- **Cel:** dokładna data doręczenia uruchamia termin.
- **Zakres:**
    - ekspedycja (wybór adresata: pełnomocnik, gdy ustanowiony – BR-06);
    - książka nadawcza;
    - rejestracja zwrotki (doręczenie do rąk, zastępcze, awizo);
    - przeliczenie terminu (T-025) i aktualizacja timera.
- **Kryteria akceptacji:**
    - [x] `DeliveryAndAppealServiceTest` (7 dni, dzień wolny, awizo +14);
    - [x] korekta uchyla stary termin, tworzy nowy i przestawia timer.
- **Zależności:** T-025, T-020. **Odwołania:** FR-25, FR-26.

### [~] T-042 Szkielet workerów integracji
- **Cel:** wspólna baza dla wszystkich integracji.
- **Zakres:**
    - moduł `workers` z `operaton-external-task-client`;
    - uwierzytelnienie client credentials w ID-users;
    - ponawianie z rosnącym odstępem, incydenty, klucze idempotencji;
    - metryki;
    - jeden JAR na temat lub jeden z konfiguracją tematów (uzasadnij w ADR).
- **Kryteria akceptacji:**
    - [~] handlery testowane z mockiem `ExternalTaskService` (complete / handleFailure z backoff); pełny cykl z silnikiem po uruchomieniu Compose; metryki `/metrics`.
- **Zależności:** T-020, T-004. **Odwołania:** D7.

### [x] T-043 Adaptery doręczeń
- **Cel:** wysyłka elektroniczna i pocztowa.
- **Zakres:**
    - interfejsy i zaślepki WireMock dla e-Doręczeń / ePUAP i operatora pocztowego (Elektroniczny Nadawca);
    - mapowanie statusów na komunikaty procesu.
- **Kryteria akceptacji:**
    - [x] `DeliveryHandlerTest` (e-Doręczenia, poczta, raport nadania, mapowanie statusów);
    - [x] klucz idempotencji `DOR-…` w nagłówku `Idempotency-Key`; `confirmDispatch` neutralne przy ponowieniu.
- **Zależności:** T-042, T-041. **Odwołania:** FR-25. **[zaślepka]** API zewnętrzne.

---

## Etap 5. Raporty, archiwum, eksploatacja

### [~] T-050 Raporty i statystyki (UC-19)
- **Zakres:**
    - wpływ i załatwienia, odsetek nadania biegu, odsetek uwzględnionych zażaleń;
    - czas wstępnej kontroli (mediana, p90), zaległości, obciążenie;
    - eksport do XLSX i CSV.
- **Kryteria akceptacji:**
    - [~] `ReportService` + `TabularExport` z testami (`ArchiveReportTemplateTest`); eksport XLSX/CSV z ekranu S-20 do podpięcia.
- **Zależności:** T-027, T-028. **Odwołania:** FR-32.

### [~] T-051 Pisma ogólne (UC-17)
- **Zakres:** rejestr pism niebędących skargami, odpowiedź z szablonu (pouczenie o wymogach skargi), termin odpowiedzi.
- **Kryteria akceptacji:**
    - [~] `GeneralLetterService` + szablon `ODP_PISMO_OGOLNE`; kwalifikacja z S-06 rejestruje pismo ogólne; odpowiedź z S-12 do spięcia.
- **Zależności:** T-013, T-026. **Odwołania:** FR-30.

### [x] T-052 Archiwizacja (UC-21)
- **Zakres:**
    - zamknięcie sprawy, kategoria archiwalna z konfiguracji;
    - paczka archiwalna (metadane XML + PDF/A), spis zdawczo-odbiorczy;
    - roczny eksport repertorium do PDF/A z sumą kontrolną.
- **Kryteria akceptacji:**
    - [x] paczka ZIP (metadata.xml, SHA256SUMS, PDF/A) sprawdzana w `ArchiveReportTemplateTest`;
    - [x] `store.put(..., lockCompliance=true)`.
- **Zależności:** T-030. **Odwołania:** FR-09, FR-34, NFR-12.

### [~] T-053 Role Ansible dla VM
- **Cel:** produkcja i przedprodukcja odtwarzalne z kodu.
- **Zakres:**
    - role: WildFly 41 + dystrybucja Operaton (standalone-ha, 2 węzły), HAProxy, PostgreSQL 17 + Patroni + etcd, OpenSearch (3 węzły), usługi systemd dla docs-service i workerów;
    - inwentarze preprod i prod;
    - sekrety poza repozytorium.
- **Kryteria akceptacji:**
    - [~] role i inwentarze w `infra/ansible` (ADR 0009); uruchomienie na VM przez zespół infrastruktury;
    - [x] wersje w `group_vars/all/main.yml` zgodne z `infra/docker`.
- **Zależności:** T-006. **Odwołania:** D11, NFR-07.
- **[decyzja]** system operacyjny VM i sposób dystrybucji pakietów.

### [~] T-054 Monitoring i logi
- **Zakres:**
    - metryki (Prometheus): JVM, pule połączeń, zadania i incydenty Operaton, kolejka OCR;
    - endpoint `/health`;
    - logi JSON do centralnego systemu;
    - alerty: incydenty, terminy bez czynności, błędy weryfikacji łańcucha audytu.
- **Kryteria akceptacji:**
    - [x] `infra/monitoring/grafana-dashboard.json`, `/health`, `/metrics` (ADR 0019);
    - [ ] alert testowy – po uruchomieniu Prometheus/Alertmanager (profil `monitoring`).
- **Zależności:** T-042, T-053. **Odwołania:** NFR-07.

### [~] T-055 Utwardzenie bezpieczeństwa
- **Zakres:**
    - przegląd OWASP ASVS (poziom 2) dla aplikacji;
    - CSP bez wyjątków, limity uploadu i weryfikacja sygnatur plików;
    - ochrona przed path traversal i open redirect;
    - przegląd uprawnień bazy;
    - przygotowanie do testu penetracyjnego;
    - dane wejściowe do DPIA.
- **Kryteria akceptacji:**
    - [x] `docs/security/asvs-checklist.md`, `dpia-dane-wejsciowe.md`, `przeglad-uprawnien-bazy.md`;
    - [ ] brak znalezisk wysokich – po pierwszym skanie Dependency-Check z kluczem NVD w CI.
- **Zależności:** etapy 1–4. **Odwołania:** NFR-04, NFR-05, NFR-13.

### [~] T-056 Aktualizacja Operaton (procedura)
- **Zakres:**
    - procedura i skrypt aktualizacji wersji Operaton (cykl 6-miesięczny);
    - test migracji schematu na kopii danych;
    - checklist zgodności WildFly.
- **Kryteria akceptacji:**
    - [~] `docs/runbooks/aktualizacja-operaton.md`, `infra/scripts/update-operaton.sh`, `test-schema-migration.sh`; próbna aktualizacja po pierwszym wydaniu.
- **Zależności:** T-053. **Odwołania:** architektura §10.

---


### [ ] T-057 Migracja do PrimeFaces 16 (ADR 0024)
- **Zakres:** `primefaces.version` → 16.x bez klasyfikatora `jakarta` (parent i `app/pom.xml`), PrimeFaces Extensions w wersji
  zgodnej z 16 (potwierdzić dostępność), przejście po przewodniku migracji 15 → 16 (usunięte/przemianowane atrybuty, CSP,
  motyw `saga`, `p:fileUpload`/`p:validateFile`, `p:dataTable`), `mvn -B verify`, `ui-tests` (Playwright + axe) na S-01…S-26,
  ręczny test logowania, wylogowania i wysyłki plików na dev; aktualizacja ADR 0001 (D2) i `doc/CLAUDE.md`.
- **Warunek startu:** zamknięte problemy środowiska dev z ID-users (T-004 `[~]` i pozostałe `[~]`).
- **Zależności:** T-001, T-004. **Odwołania:** ADR 0024, D2.
## Notatki z realizacji

Po każdym zadaniu dopisz tu jedną linię w formacie: `T-xxx – data – co zrobiono, co zostało, link do ADR`.

Oznaczenie `[~]` = zadanie rozpoczęte, część kryteriów spełniona.

- T-001 – 03.11.2026 – parent `pom.xml` (Java 21, BOM-y Operaton 2.2.0, JUnit 6, Testcontainers, AWS SDK; PrimeFaces 15.0.19 `jakarta`), moduły `app`/`docs-service`/`workers`, Enforcer (Java 21, zakaz duplikatów, zakaz Spring/Quarkus/Lombok w deklaracjach wprost), `.editorconfig`, `.gitignore`; `mvn -B verify` zielone (102 testy). ADR: `docs/adr/0001-stos-technologiczny.md`. Zostało: profil `-Pit` ma konfigurację, ale jeszcze bez testów Testcontainers.
- Front (paczki 1–4 makiet „eSkarga”) – 03.11.2026 – 27 ekranów S-01…S-27 jako Facelets z szablonem układu (górny pasek, menu zależne od roli, okruszki, tytuł z paskiem akcji, stopka), arkusz CSS z makiet + nakładka PrimeFaces, komponenty kompozytowe `tk:*` (status, termin, sygnatura, flagi, baner, komunikat, kafelek sprawy, KPI, oś czasu, słupki, kartka dokumentu, skutki, stan pusty), strona arkusza komponentów `dev/komponenty.xhtml`, beany `@ViewScoped` zasilane danymi przykładowymi (ADR 0004). Zostało: uruchomienie na WildFly 41 + Operaton (brak serwera i Dockera w sesji), audyt axe-core, testy Playwright, widżet pdf.js (T-034), podmiana `DemoDataset` na usługi domenowe.
- T-002 – 03.11.2026 – `infra/docker/compose.yml` (obraz dystrybucji Operaton dla WildFly 41 z CLI źródła danych, PostgreSQL 17 z rolami, SeaweedFS, OpenSearch, SoftHSM2, mock OIDC z konfigurowalnymi claimami, WireMock, workery), `.env.example`, README z portami. Zostało: pierwsze uruchomienie (demon Dockera niedostępny w sesji), health check Cockpitu do potwierdzenia.
- T-003 – 03.11.2026 – skrypt CLI `eskarga-datasource.cli` (XA, schematy `repertorium` + `operaton`), role bazy w `init-roles.sh` (aplikacja bez UPDATE/DELETE, migracje osobno), Flyway V1 (`docket_counter`, `audit_event`, `holiday`, `delegation`) i V2 (model domeny §8.1), `persistence.xml`. Zostało: uruchomienie Flyway przy starcie (bean startowy), test integracyjny braku UPDATE/DELETE, encje JPA poza `DocketCounter`/`AuditEvent` (T-010).
- T-004 – 03.11.2026 – `OidcSecurityConfig` (Jakarta Security, PKCE, klient poufny), konfigurowalne claimy w `OidcSettings`, `CurrentUser`, tryb `demo` wyłączany rozszerzeniem CDI, ciasteczko `HttpOnly`/`Secure`/`SameSite=Strict` (filtr), wylogowanie. ADR 0002. Zostało: filtr MFA (`acr`/`amr`), interceptor `identityService.setAuthentication`, back-channel logout, test „bez MFA → 403”, test Playwright braku tokenu – wszystko po poznaniu kontraktu ID-users.
- T-005 – 03.11.2026 – szablon Facelets, motyw (saga + nakładka), `messages_pl.properties`, strony 403/404/500/MFA/sesja (S-26), nagłówki bezpieczeństwa (filtr) i CSP przez PrimeFaces. ADR 0003 (teksty UI). Zostało: audyt axe-core po uruchomieniu.
- T-012 – 03.11.2026 – `DocketNumberAllocator` + `DocketNumberService` (`SELECT … FOR UPDATE`, `MANDATORY`), test współbieżności 50/10 na repozytorium w pamięci. Zostało: ten sam test na PostgreSQL (Testcontainers).
- T-015 – 03.11.2026 – `AuditEvent`, `AuditHash` (SHA-256 z poprzednim skrótem), `AuditChainVerifier`, `AuditService` (singleton). Zostało: interceptor CDI na czynnościach, wysyłka syslog JSON do SIEM, polecenie weryfikacji z ekranu S-25, pokrycie 100% czynności.
- T-016 – 03.11.2026 – `CaseAccessPolicy` (trzy poziomy D4, BR-10) z testami, `ScreenAccessFilter`, ekrany S-22/S-23. Zostało: repozytoria przydziałów i upoważnień (JPA), raport przeglądu uprawnień, zdarzenia audytu przy odmowie.
- T-020 – 03.11.2026 – `bpmn/wstepna-kontrola.bpmn` (`isExecutable`, `historyTimeToLive=P180D`, grupy `tk-biuro-podawcze`/`tk-referenci`/`tk-prezes`/`tk-sklad`, `assignee=${sedziaId}`, timery `${terminBrakow}`/`${terminZazalenia}`, warunki bramek, `T_Przekaz` jako external task `przekazanie-merytoryczne`), `processes.xml`, `EskargaProcessApplication` (EJB), `ProcessVariables` (lista dozwolonych), `PreliminaryReviewProcess` (start po kluczu = sygnatura, korelacja komunikatów, przestawianie timera). Zostało: `historyTimeToLive` z konfiguracji (Operaton nie przyjmuje wyrażenia – parse listener), external tasks doręczeń w modelu (podproces), wdrożenie na WildFly.
- T-021 – 03.11.2026 – `WstepnaKontrolaProcessTest` (operaton-bpm-junit5 + assert, H2): 8 ścieżek, wszystkie węzły i przejścia. Zostało: CI (T-006).
- T-025 – 03.11.2026 – `DeadlineCalculator`, `PolishHolidays` (święta ustawowe + ruchome, dni wolne TK), `Suspension`; 40 przypadków testowych. Zostało: potwierdzenie zasad przez Biuro Służby Prawnej [decyzja], kalendarz z tabeli `holiday`, aktualizacja zmiennej i timera z ekranu S-10.
- T-026 – 03.11.2026 – `docs-service`: `FreemarkerTemplateRenderer` w piaskownicy (ALLOWS_NOTHING_RESOLVER, bez `?api`, escapowanie HTML, limit czasu i rozmiaru) z 7 testami, `PdfRenderer` + `OpenHtmlToPdfRenderer` (PDF/A-2b), `PdfAValidator` (interfejs), szablon zarządzenia o brakach. Zostało: veraPDF, pozostałe szablony, tabela `letter_template` w użyciu, testy wizualne, czcionki w dev.
- T-033 – 03.11.2026 – ekran S-14 (`akta/podglad.xhtml`): nagłówek sprawy z terminem, pasek przebiegu, spis akt z zakładkami Akta jawne / Notatki wewnętrzne, podgląd dokumentu (warstwa OCR, dokument niejawny jako plansza), widok dzielony, panel Czynności/Terminy/Lista kontrolna/Metadane, warianty ról i statusów. Zostało: pdf.js (T-034), dane z usług domenowych, axe-core, 1366×768 w przeglądarce.
- T-042 – 03.11.2026 – moduł `workers` z `operaton-external-task-client`, `AbstractIdempotentHandler` (klucz idempotencji, rosnący odstęp, incydent po 5 próbach), handlery-zaślepki doręczeń i przekazania, konfiguracja ze środowiska, obraz Docker. Zostało: token client credentials z ID-users, test pełnego cyklu z silnikiem, metryki.

- Partia 2 (03.11.2026) – realizacja wszystkich zadań z decyzjami jako ADR 0005–0020 (do potwierdzenia po zakończeniu prac):
- T-010 – 03.11.2026 – encje JPA §8.1 (wersjonowane wpisy, strony, dokumenty, czynności, doręczenia, terminy, zażalenia, wgląd, publikacja, szablony, słowniki, archiwum), migracje V1–V5 + V5.1 (PostgreSQL), `ModelSchemaTest` (Flyway + walidacja schematu na H2, ADR 0011), diagram ER. Zostało: uruchomienie `-Pit` z Dockerem.
- T-011 – 03.11.2026 – `IncomingRegister` (DW rrrr/nnnnn pod blokadą, kwalifikacja, kolejka, zaległe), ekran S-03 zapisuje przez usługę. Zostało: upload skanów z formularza (`CaseDocumentService.upload` gotowe, `p:fileUpload` do podpięcia), etykieta PDF.
- T-013 – 03.11.2026 – `CaseFileService.createCase` (D1: sygnatura → wpis → strony → orzeczenie → karty → start procesu; wycofanie bez zużycia numeru), `attachLetter`, S-06/S-07 przez usługi. ADR 0013 (start procesu domyka zadania rejestracyjne).
- T-014 – 03.11.2026 – `CorrectionService` (wniosek → decyzja kierownika jako osobny wiersz, ADR 0016), S-09 wysyła/zatwierdza/odrzuca.
- T-015 – 03.11.2026 – `recordDenial` w REQUIRES_NEW (ADR 0014), `recent/forCase`, weryfikacja łańcucha z S-25, podpis łańcucha kluczem HSM w usłudze dokumentów. Zostało: syslog do SIEM, harmonogram podpisu dziennego.
- T-016 – 03.11.2026 – `DelegationService`, `CaseAccessService`, odmowy w audycie, S-23 dodaje/odwołuje, raport przydziałów w S-22.
- T-020 – 03.11.2026 – external tasks doręczeń (`doreczenie-pisma`), nasłuch `transferCompletedListener`, `sygnaturaPowiazana`, HTTL z `eskarga.process.httl.days`.
- T-022 – 03.11.2026 – `TaskBoardService` (TaskService + CaseAccessPolicy), pulpit S-01 z `JpaViewData` (ADR 0017), widok kierownika `dueWithin`.
- T-023 – 03.11.2026 – `JudgeAssignmentService` (BR-10, zarządzenie, przydział, assignee, audyt), S-17 `assign()`.
- T-024 – 03.11.2026 – `wymogi-formalne.dmn` + `FormalRequirementsService` (ADR 0015); lista kontrolna S-08 z DMN. Zostało: zapis stanów listy jako czynność.
- T-025 – 03.11.2026 – kalendarz z tabeli `holiday` (`HolidayService`), korekta doręczenia przestawia timer (`DeliveryService`). [decyzja] zasady do potwierdzenia przez BSP (ADR 0006).
- T-026 – 03.11.2026 – 8 szablonów, veraPDF (OutputIntent sRGB), `LetterService` (projekt z aktywnej wersji, wynik kontroli), `LetterModelBuilder`, `DocsServiceClient`. Zostało: testy wizualne, czcionka urzędowa TK.
- T-026a – 03.11.2026 – walidacja/podgląd/aktywacja z S-24 (`AdministracjaBean`), `TemplateService.seedDefaults`. Zostało: edytor z podświetlaniem (`pe:codeMirror`) w widoku.
- T-027 – 03.11.2026 – `AppealService` (terminowość pomocnicza, BR-07, skład 3 sędziów, BR-10). Zostało: akcje w S-11/S-18.
- T-028 – 03.11.2026 – `MeritTransferHandler` (WireMock, Idempotency-Key), `TransferResource`, `markTransferred`, zawiadomienia art. 63 jako szablon.
- T-030 – 03.11.2026 – `DocumentServlet` (dostęp, Range, SHA-256, znak wodny, audyt), `CaseDocumentService.upload`. Zostało: test na SeaweedFS.
- T-031 – 03.11.2026 – usługa dokumentów: `/dokumenty/przetworz` (PubSec, DSS, OCR, PDF/A, tekst), worker `dokument-przetworz`, `applyProcessing`. ADR 0008 (podpis lokalny).
- T-032 – 03.11.2026 – `SearchIndex` (pamięć/OpenSearch), `SearchService` z filtrem D4, indeksowanie przy wpisie i po OCR, S-02 (ADR 0018). Zostało: pomiar ≤ 2 s na OpenSearch.
- T-034 – 03.11.2026 – `tk:pdfViewer` (pdf.js z webjara, worker lokalny, bez eval, Range, leniwe strony, zoom, karta/strona, szukanie, skróty J/K/+/−//), znak wodny przy pobraniu, CSP `worker-src`. ADR 0007. Zostało: uruchomienie testów Playwright.
- T-035 – 03.11.2026 – `NoteService`, nakładka zaznaczeń (`highlightsJson`), cytat „Ts 312/26, k. 3”.
- T-036 – 03.11.2026 – `AccessRequestService` (BR-09, art. 80), `PdfRedactor` (rastrowo), `Anonymizer` (tekst).
- T-037 – 03.11.2026 – `HsmKeyStore` (SunPKCS11; pkcs12 tylko poza PROD), `PubSecDecryptor`, `AuditChainSigner`, skan repozytorium. Zostało: test na SoftHSM2, PubSec z kluczem w HSM (PDFBox wymaga magazynu eksportowalnego – do obejścia własnym `PublicKeySecurityHandler`).
- T-040 – 03.11.2026 – `TwoStepPadesSigner` (DSS, sesja skrótu, TSA opcjonalna), `SigningService` (jedna transakcja), S-16 `begin/complete`, kontrakt `LocalSignerClient` (ADR 0008 [decyzja]).
- T-041 – 03.11.2026 – `DeliveryService` (BR-06, zwrotka, awizo +14, korekta, API dla workera), S-10.
- T-042 – 03.11.2026 – token client credentials (`common`), `JsonHttp`, metryki, konfiguracja tematów (ADR 0010). Zostało: pełny cykl z silnikiem.
- T-043 – 03.11.2026 – `DeliveryHandler` (e-Doręczenia/ePUAP, poczta), mapowanie statusów, testy WireMock.
- T-050 – 03.11.2026 – `ReportService`, `TabularExport` (CSV/XLSX), S-20 z danych. Zostało: przyciski eksportu.
- T-051 – 03.11.2026 – `GeneralLetterService` (30 dni), szablon odpowiedzi, kwalifikacja z S-06.
- T-052 – 03.11.2026 – `ArchiveService` (gotowość, paczka ZIP, Object Lock, spis n/rrrr, księga roczna XHTML), S-21 `transfer()`. ADR 0012.
- T-053 – 03.11.2026 – role Ansible (wildfly_operaton, eskarga_services, haproxy, postgresql_patroni, opensearch), inwentarze preprod/prod, wzór Vault (ADR 0009). Zostało: uruchomienie na VM.
- T-054 – 03.11.2026 – `/health`, `/metrics`, metryki workerów, Prometheus/Grafana/alerty w `infra/monitoring`, logi JSON (ADR 0019). Zostało: alert testowy.
- T-055 – 03.11.2026 – lista ASVS, DPIA, przegląd uprawnień bazy, `SafeRedirect`, walidacja sygnatury PDF, skan sekretów. Zostało: pierwszy skan Dependency-Check z kluczem NVD, test penetracyjny.
- T-056 – 03.11.2026 – runbook i skrypty aktualizacji/testu migracji. Zostało: próbna aktualizacja.
- T-002/T-003/T-004/T-005/T-006 – 03.11.2026 – uzupełnienia: usługa `flyway` i OpenSearch z `analysis-stempel` w Compose, profil `monitoring`, filtr MFA, uwierzytelnienie Operaton, back-channel logout, moduł `common`, workflow CI, testy UI (`ui-tests/`, ADR 0020). Wspólne „zostało”: pierwsze uruchomienie na WildFly 41 + Docker (brak demona Dockera i serwera w sesji).
- Decyzje użytkownika (03.11.2026, partia 3): ad 1 kontrakt ID-users wdrożony (ADR 0002: `resource_access.<client>.roles`, `acr` haslo/mfa/wysoki + `acr_values`, `private_key_jwt`, `azp`, Keycloak dev w Compose zamiast mock OIDC); ad 2 podpis kluczem YubiKey z certyfikatem niekwalifikowanym – moduł `local-signer` (ADR 0008); ad 3–5 ADR 0006/0015 i szablony zaakceptowane; ad 6 zaślepki WireMock jako kontrakt (ADR 0021); ad 7–9, 11, 12 ADR 0005/0009/0007/0016/0013 zaakceptowane; ad 10 okres przechowywania konfigurowalny (`eskarga.archive.retention.*`, `DictionaryService.updateArchiveCategory`); ad 13 MinIO z Object Lock w Compose (ADR 0022); ad 14 propozycja TSA – serwer dev RFC 3161 + kwalifikowany dostawca na produkcji (ADR 0023, do decyzji); ad 15 porty i ustawienia Dockera wg kontraktu (Keycloak :8180) – projekt „Aplikacja zgodna z projektem ID-users” nie był dostępny w artefaktach.
- Decyzja (03.11.2026): ad 14 – produkcyjny znacznik czasu KIR Szafir (ADR 0023 zaakceptowany); `TimestampSources` obsługuje konto KIR (HTTP Basic) albo certyfikat klienta; dev pozostaje na serwerze `tsa`. Zostało: umowa z KIR i dane dostępu w Vault, łańcuch certyfikatów TSA w magazynie zaufania.
- 04.11.2026 – Compose eSkargi przygotowany do pracy obok środowiska ID-users (projekt `id`): osobna nazwa projektu, porty 8082/9992/5433/9100/9101, logowanie przez Keycloak ID-users (:8180) z rejestracją klientów skryptem `zarejestruj-w-id-users.sh` (profil `id-users`), własny Keycloak tylko w profilu `oidc-local` (:8181).
- Pierwsze uruchomienie dev (05.11.2026): rejestracja klientów w Keycloaku ID-users przez Admin REST API z Basha (kcadm odrzucany przez Keycloak ID-users – „Cannot parse the JSON”, przyczyna otwarta); skrypty Windows `infra/windows`; MinIO zniknęło z Docker Hub → RustFS + aws-cli, kontrola egzekwowania Object Lock w `s3-init` (ADR 0022 zmieniona); `S3DocumentStore`: RetainUntilDate, SSE i sumy kontrolne konfigurowalne. Zostało: start `infra\windows\start.cmd` do końca, logowanie przez ID-users (Elytron OIDC/Apache HttpClient – ryzyko jak kcadm).
- Decyzja użytkownika (05.11.2026): logowanie dev przez Jakarta Security bez PKCE (wariant 1 z ADR 0002, `ESKARGA_PKCE=`); mechanizm Elytron OIDC z PKCE S256 – zadanie przed TEST.
- 05.11.2026: wariant 1 niewykonalny – polityka klientów realmu id-users wymusza PKCE (pkce-enforcer) dla wszystkich klientów; do realizacji wariant 2 (ADR 0002).
- 05.11.2026: zrealizowany wariant 2 (ADR 0002) – Elytron OIDC z PKCE S256 (`WEB-INF/oidc.json`, `ElytronTokens`), `default.acr.values=mfa` w kliencie Keycloaka; weryfikacja logowania na środowisku dev w toku.
- 05.11.2026 – logowanie przez ID-users potwierdzone na środowisku dev (Windows, Docker Desktop, Keycloak ID-users 26.7.2): Elytron OIDC
  z PKCE S256, wyzwalacz `/callback` (adres z rejestru ID-users), klient `eskarga` z `private_key_jwt` (własny dostawca `jwt-eskarga`,
  klucz PEM i `kid` z rejestracji T-037 projektu id, `aud` = publiczny adres tokenu). Do sprawdzenia: role na pulpicie dla kont z
  `ESKARGA_UZYTKOWNICY`, MFA (`default.acr.values=mfa`), wylogowanie, tokeny usługi dokumentów i workerów (`private_key_jwt`),
  kontenery `tsa` (kod 127) i `softhsm` (kod 255) zatrzymane przy starcie.

