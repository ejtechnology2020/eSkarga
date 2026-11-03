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

### [ ] T-001 Szkielet repozytorium i budowania
- **Cel:** projekt Maven multi-module zgodny ze strukturą z `CLAUDE.md`.
- **Zakres:**
    - parent `pom.xml` z Java 21, BOM-ami Operaton 2.2, Jakarta EE 11 i PrimeFaces 15 (`jakarta`);
    - moduły `app` (WAR), `docs-service` (WAR), `workers` (JAR);
    - Maven Enforcer (wersja Javy, zakaz duplikatów zależności), formatowanie kodu, `.editorconfig`, `.gitignore`;
    - szkielet `docs/adr/0001-stos-technologiczny.md` z decyzjami D1–D13.
- **Kryteria akceptacji:**
    - [ ] `mvn -B verify` przechodzi na pustym projekcie;
    - [ ] WAR-y budują się bez ostrzeżeń o konfliktach wersji.
- **Zależności:** brak. **Odwołania:** D2, D9.
- **Ustalone:** pakiet bazowy `pl.ejtechnology.tk.eSkarga`, groupId `pl.ejtechnology.tk`, artifactId `eskarga-*` (3.11.2026).

### [ ] T-002 Środowisko dev w Docker Compose
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

### [ ] T-003 Baza danych i migracje
- **Cel:** schematy `repertorium` i `operaton` w jednej bazie, jedno źródło danych w WildFly.
- **Zakres:**
    - definicja datasource w CLI WildFly (skrypt `.cli` w repozytorium);
    - Flyway dla schematu `repertorium` (V1: tabele techniczne, `audit_event`, `docket_counter`);
    - schemat Operaton ze skryptów dystrybucji;
    - użytkownik aplikacji bez uprawnień UPDATE/DELETE na tabelach repertorium (rola osobna dla migracji).
- **Kryteria akceptacji:**
    - [ ] migracje uruchamiają się przy starcie aplikacji w dev;
    - [ ] test integracyjny potwierdza brak uprawnień UPDATE/DELETE dla roli aplikacji.
- **Zależności:** T-002. **Odwołania:** D1, NFR-01.

### [ ] T-004 Logowanie OIDC przez ID-users
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
    - [ ] test integracyjny: bez MFA → 403, z MFA → dostęp;
    - [ ] żaden token nie trafia do przeglądarki (sprawdzone w teście Playwright);
    - [ ] ADR z nazwami claimów do potwierdzenia z zespołem ID-users.
- **Zależności:** T-002. **Odwołania:** D3, NFR-04. **[zaślepka]** kontrakt ID-users.

### [ ] T-005 Szablon interfejsu JSF
- **Cel:** wspólny layout, nawigacja i komunikaty dla wszystkich ekranów.
- **Zakres:**
    - szablon Facelets (nagłówek z użytkownikiem i rolą, menu zależne od roli, obszar treści);
    - motyw PrimeFaces stonowany (urzędowy);
    - `messages_pl.properties`, strony błędów 403, 404 i 500 bez szczegółów technicznych;
    - nagłówki bezpieczeństwa (CSP, HSTS, X-Frame-Options, Referrer-Policy) przez filtr.
- **Kryteria akceptacji:**
    - [ ] ekran startowy działa po zalogowaniu;
    - [ ] audyt axe-core bez błędów krytycznych;
    - [ ] CSP bez `unsafe-eval`.
- **Zależności:** T-004. **Odwołania:** NFR-10.

### [ ] T-006 Potok CI
- **Cel:** każda zmiana jest automatycznie budowana i testowana.
- **Zakres:**
    - build, testy jednostkowe i procesowe, testy integracyjne (Testcontainers);
    - skan zależności (OWASP Dependency-Check lub równoważny);
    - artefakty WAR/JAR z sumami kontrolnymi.
- **Kryteria akceptacji:**
    - [ ] potok zielony na gałęzi głównej;
    - [ ] build kończy się błędem przy podatności o wysokim priorytecie.
- **Zależności:** T-001. **Odwołania:** NFR-13.
- **[decyzja]** platforma CI (GitLab CI, Jenkins, GitHub Actions).

---

## Etap 1. Rdzeń repertorium

### [ ] T-010 Model domeny (JPA)
- **Cel:** encje z sekcji 8.1 analizy wymagań.
- **Zakres:**
    - encje `IncomingLetter`, `CaseFile`, `Party`, `Attorney`/`PowerOfAttorney`, `ReviewSubject`, `FinalRuling`, `ProceduralAct`, `Document`, `Delivery`, `Deadline`, `Appeal`, `AccessRequest`, `Delegation`;
    - migracje Flyway, wersjonowanie wpisów zamiast UPDATE;
    - słowniki: sądy, organy, typy pism, dni wolne.
- **Kryteria akceptacji:**
    - [ ] testy repozytoriów na Testcontainers;
    - [ ] diagram ER wygenerowany do `docs/`.
- **Zależności:** T-003. **Odwołania:** analiza §8.1.

### [ ] T-011 Dziennik wpływu i rejestracja pisma (UC-01)
- **Cel:** biuro podawcze rejestruje pismo w mniej niż 3 minuty.
- **Zakres:**
    - formularz: kanał, data wpływu, data wniesienia (stempel lub UPO, z flagą „niepewna”), nadawca, liczba załączników i odpisów;
    - upload skanów;
    - numer dziennika, etykieta z kodem kreskowym (PDF), kolejka „Do kwalifikacji”;
    - wariant dla dokumentu niejawnego: zapis wyłącznie metadanych.
- **Kryteria akceptacji:**
    - [ ] scenariusze główny i alternatywne 2a i 3a z UC-01 pokryte testami;
    - [ ] zdarzenie w dzienniku audytu.
- **Zależności:** T-010, T-015. **Odwołania:** FR-01 – FR-03, UC-01.

### [ ] T-012 Generator sygnatur (BR-01)
- **Cel:** sygnatury Ts n/rr i Tw n/rr bez luk i duplikatów.
- **Zakres:**
    - `DocketNumberService`: tabela `docket_counter(symbol, year, last_value)` z `SELECT … FOR UPDATE` w transakcji wywołującego;
    - reset numeracji co rok.
- **Kryteria akceptacji:**
    - [ ] test współbieżności: 50 równoległych transakcji, w tym 10 wycofanych, daje numery 1…40 bez luk;
    - [ ] numer nigdy nie jest używany ponownie.
- **Zależności:** T-010. **Odwołania:** BR-01, FR-06.

### [ ] T-013 Kwalifikacja pisma i założenie sprawy (UC-02, UC-03, UC-04)
- **Cel:** referent zamienia pismo w sprawę Ts/Tw albo dołącza je do sprawy w toku.
- **Zakres:**
    - kolejka pism do kwalifikacji, wybór typu (Ts, Tw, pismo do sprawy, pismo ogólne);
    - podpowiedzi powiązań (ten sam skarżący, pełnomocnik, przepis);
    - karta sprawy (strony, przedmiot, wzorce, orzeczenie ostateczne);
    - zatwierdzenie w jednej transakcji: nadanie sygnatury (T-012), wpis, start procesu Operaton (kluczem biznesowym jest sygnatura).
- **Kryteria akceptacji:**
    - [ ] test: błąd startu procesu wycofuje wpis i nie zużywa numeru;
    - [ ] pismo do sprawy w toku wysyła odpowiedni komunikat do procesu (uzupełnienie, zażalenie).
- **Zależności:** T-012, T-020. **Odwołania:** FR-04 – FR-07, D1.

### [ ] T-014 Korekta wpisu w repertorium (UC-22)
- **Cel:** korekta bez nadpisywania historii.
- **Zakres:** wniosek referenta z uzasadnieniem, akceptacja kierownika, nowa wersja wpisu, podgląd historii wersji.
- **Kryteria akceptacji:**
    - [ ] brak operacji UPDATE na tabelach repertorium;
    - [ ] historia widoczna w karcie sprawy.
- **Zależności:** T-013. **Odwołania:** FR-08, NFR-01.

### [ ] T-015 Dziennik zdarzeń z łańcuchem skrótów
- **Cel:** niezaprzeczalny ślad każdej czynności i każdego odczytu dokumentu.
- **Zakres:**
    - `AuditService` (interceptor CDI i wywołania jawne);
    - pola: kto, rola, co, obiekt, sprawa, czas, adres, skrót poprzedniego wpisu i własny skrót;
    - wysyłka do SIEM (syslog JSON);
    - polecenie weryfikacji łańcucha.
- **Kryteria akceptacji:**
    - [ ] test: modyfikacja wpisu w bazie jest wykrywana przez weryfikator;
    - [ ] 100% czynności z UC-01 – UC-24 generuje zdarzenia.
- **Zależności:** T-003. **Odwołania:** NFR-02.

### [ ] T-016 Uprawnienia do spraw i upoważnienia
- **Cel:** kontrola dostępu na poziomie sprawy oraz czynności „za Prezesa”.
- **Zakres:**
    - `CaseAccessPolicy` (przydział sędziego, zespołu, referenta; role ogólne);
    - `Delegation` (osoba, funkcja, od–do, akt podstawowy);
    - weryfikacja upoważnienia w dacie czynności (BR-10);
    - ekran administracji upoważnieniami;
    - raport przeglądu uprawnień.
- **Kryteria akceptacji:**
    - [ ] testy: asystent nie widzi notatek spraw innego sędziego;
    - [ ] czynność Prezesa bez ważnego upoważnienia → odmowa i zdarzenie audytu.
- **Zależności:** T-004, T-010. **Odwołania:** D4, BR-10, FR-36.

---

## Etap 2. Proces wstępnej kontroli

### [ ] T-020 Model BPMN w aplikacji
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
    - [ ] model wdraża się w dev;
    - [ ] żadna zmienna procesu nie zawiera danych osobowych (test sprawdzający nazwy i typy zmiennych).
- **Zależności:** T-002, T-004. **Odwołania:** D1, D6, D8, D9.

### [ ] T-021 Testy procesowe
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
    - [ ] 100% przejść (sequence flow) pokrytych;
    - [ ] testy w CI.
- **Zależności:** T-020. **Odwołania:** UC-07, UC-10 – UC-12.

### [ ] T-022 Pulpit zadań (UC-18)
- **Cel:** użytkownik widzi swoje zadania i terminy w jednym miejscu.
- **Zakres:**
    - lista zadań z `TaskService` filtrowana przez `CaseAccessPolicy`;
    - kolumny: sygnatura, zadanie, termin, liczba dni;
    - sortowanie po terminie;
    - widok kierownika: zaległości i obciążenie.
- **Kryteria akceptacji:**
    - [ ] termin ≤ 2 dni oznaczony tekstem i kolorem;
    - [ ] Tasklist Operaton nie jest używany.
- **Zależności:** T-020, T-016. **Odwołania:** FR-33, UC-18.

### [ ] T-023 Wyznaczenie sędziego (UC-05)
- **Cel:** Prezes lub osoba upoważniona wyznacza sędziego do wstępnej kontroli.
- **Zakres:** zadanie Prezesa z podglądem obciążenia sędziów, zarządzenie o wyznaczeniu, przydział do sprawy, sprawdzenie upoważnienia.
- **Kryteria akceptacji:**
    - [ ] zarządzenie w aktach;
    - [ ] sędzia staje się `assignee` zadania kontroli;
    - [ ] zdarzenie audytu.
- **Zależności:** T-016, T-020. **Odwołania:** FR-14, UC-05.

### [ ] T-024 Lista kontrolna wymogów formalnych (DMN, UC-06)
- **Cel:** lista wymogów dobierana do typu sprawy bez zmian w kodzie.
- **Zakres:**
    - tabela DMN `wymogi-formalne` (skarga: art. 53; wniosek: art. 47–49);
    - ekran listy kontrolnej (spełniony / brak / do sprawdzenia, komentarz);
    - weryfikacja pełnomocnika i wyjątków zawodowych (BR-05).
- **Kryteria akceptacji:**
    - [ ] zmiana DMN zmienia listę bez wdrożenia WAR;
    - [ ] testy DMN dla skargi i wniosku.
- **Zależności:** T-020. **Odwołania:** FR-11, FR-13, BR-05.

### [ ] T-025 Kalkulator terminów (BR-02 – BR-04)
- **Cel:** poprawne wyliczanie terminów 7-dniowych i 3-miesięcznych.
- **Zakres:**
    - `DeadlineCalculator`: początek od doręczenia, dni wolne z konfigurowalnego kalendarza, przesunięcie na dzień roboczy;
    - wstrzymanie na czas wniosku o pełnomocnika z urzędu;
    - data nadania pocztowego lub UPO jako data wniesienia;
    - aktualizacja zmiennej i timera przy korekcie doręczenia.
- **Kryteria akceptacji:**
    - [ ] testy parametryczne na ≥ 30 przypadkach (święta ruchome, koniec miesiąca, rok przestępny, wstrzymanie);
    - [ ] w UI termin opisany jako „wyliczenie pomocnicze”.
- **Zależności:** T-010. **Odwołania:** D6, FR-12, FR-17.
- **[decyzja]** zasady liczenia (dni wolne, doręczenie zastępcze) – do potwierdzenia przez Biuro Służby Prawnej.

### [ ] T-026 Pisma z szablonów – FreeMarker + OpenHTMLtoPDF (D13)
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
    - [ ] każdy szablon generuje PDF, który przechodzi veraPDF (PDF/A-2b);
    - [ ] braki zaznaczone na liście kontrolnej trafiają do treści zarządzenia;
    - [ ] testy piaskownicy i testy na znakach diakrytycznych przechodzą;
    - [ ] testy wizualne: porównanie wygenerowanego PDF z wzorcem z dopuszczalną różnicą;
    - [ ] generowanie jednego pisma trwa poniżej 2 s.
- **Zależności:** T-024, T-031. **Odwołania:** FR-15, FR-16, D13.

### [ ] T-026a Edytor szablonów (S-24)
- **Cel:** administrator merytoryczny zarządza szablonami bez wdrożenia.
- **Zakres:**
    - ekran listy szablonów z wersjami;
    - edytor kodu szablonu z podświetlaniem składni;
    - podgląd PDF na danych testowych (sprawa Ts 312/26 z danych fikcyjnych);
    - walidacja przed aktywacją (kompilacja szablonu, render, veraPDF);
    - aktywacja z akceptacją drugiej osoby;
    - historia zmian w dzienniku zdarzeń.
- **Kryteria akceptacji:**
    - [ ] szablon z błędem składni nie może zostać aktywowany;
    - [ ] aktywacja jest widoczna w dzienniku zdarzeń;
    - [ ] pisma już wygenerowane nie zmieniają się po aktywacji nowej wersji.
- **Zależności:** T-026, T-015. **Odwołania:** FR-35, NFR-15, D13.

### [ ] T-027 Zażalenie (UC-11)
- **Cel:** rejestracja zażalenia, kontrola terminu, skierowanie do składu.
- **Zakres:**
    - rejestr zażaleń, ocena terminowości (wynik pomocniczy);
    - zadanie Prezesa: skład 3 sędziów i termin posiedzenia;
    - wynik składu;
    - blokada ponownego zażalenia (BR-07).
- **Kryteria akceptacji:**
    - [ ] ścieżki z T-021 działają z UI;
    - [ ] sygnatura Ts/Tw bez zmian.
- **Zależności:** T-023, T-025. **Odwołania:** FR-18, UC-11.

### [ ] T-028 Nadanie biegu i przekazanie (UC-12)
- **Cel:** przekazanie sprawy do repertorium SK, K lub U.
- **Zakres:**
    - external task `przekazanie-merytoryczne` z kluczem idempotencji;
    - powiązanie sygnatur Ts ↔ SK;
    - sprawa Ts/Tw tylko do odczytu z banerem;
    - zawiadomienia (art. 63).
- **Kryteria akceptacji:**
    - [ ] test z WireMock: ponowienie nie tworzy duplikatu;
    - [ ] po przekazaniu edycja jest zablokowana.
- **Zależności:** T-042. **Odwołania:** FR-19, BR-08. **[zaślepka]** API repertoriów SK/K/U.

---

## Etap 3. Dokumenty i podgląd akt

### [ ] T-030 Repozytorium dokumentów (S3)
- **Cel:** bezpieczny zapis i odczyt plików akt.
- **Zakres:**
    - `DocumentStore` na AWS SDK v2 (API S3, bez zależności od dostawcy);
    - Object Lock (compliance) dla dokumentów zamkniętych, wersjonowanie, SSE;
    - SHA-256 w bazie i weryfikacja przy odczycie.
- **Kryteria akceptacji:**
    - [ ] testy na SeaweedFS;
    - [ ] niezgodny skrót daje błąd i zdarzenie audytu.
- **Zależności:** T-002, T-010. **Odwołania:** D5, D12.

### [ ] T-031 Usługa dokumentów
- **Cel:** przetwarzanie dokumentów poza aplikacją główną.
- **Zakres** (`docs-service`):
    - OCR (OCRmyPDF, język polski), konwersja i walidacja PDF/A-2b (veraPDF);
    - walidacja podpisów EU DSS (PAdES/XAdES/CAdES) z raportem;
    - wywołania jako external tasks (`dokument-przetworz`) z ponawianiem.
- **Kryteria akceptacji:**
    - [ ] skan z OCR jest przeszukiwalny;
    - [ ] raport walidacji podpisu w metadanych;
    - [ ] dokument z nieważnym podpisem dostaje ostrzeżenie.
- **Zależności:** T-030. **Odwołania:** NFR-11.

### [ ] T-032 Wyszukiwanie (UC-20)
- **Cel:** wyszukiwanie spraw i dokumentów.
- **Zakres:**
    - indeks OpenSearch z analizatorem polskim (tekst OCR, metadane, przepisy);
    - filtrowanie wyników przez `CaseAccessPolicy` w aplikacji;
    - ekran wyszukiwania;
    - odbudowa indeksu z bazy.
- **Kryteria akceptacji:**
    - [ ] wyniki ≤ 2 s przy danych testowych;
    - [ ] brak wyników ze spraw bez uprawnień.
- **Zależności:** T-031, T-016. **Odwołania:** FR-31, NFR-08.

### [ ] T-033 Ekran podglądu akt – układ i dane
- **Cel:** ekran zgodny z briefem (sekcje 3–5).
- **Zakres:**
    - stały nagłówek sprawy (sygnatura, status, biegnący termin, flagi, akcje);
    - `p:splitter` z trzema kolumnami;
    - spis akt (tomy, karty, chronologia, zakładki Akta jawne / Notatki wewnętrzne);
    - panel boczny (Terminy, Czynności, Lista kontrolna, Metadane);
    - warianty ról i statusów;
    - dokument niejawny jako plansza bez podglądu.
- **Kryteria akceptacji:**
    - [ ] sygnatura, status i termin widoczne bez przewijania w 1366×768;
    - [ ] warianty z §5 briefu;
    - [ ] axe-core bez błędów krytycznych.
- **Zależności:** T-022, T-030. **Odwołania:** brief, FR-23.

### [ ] T-034 Widżet PDF (pdf.js)
- **Cel:** podgląd dokumentu z widokiem dzielonym, bez łamania layoutu JSF.
- **Zakres:**
    - komponent kompozytowy JSF z `pdfjs-dist` (worker z własnej domeny, `isEvalSupported: false`);
    - endpoint `/akta/{caseId}/dokument/{docId}` z autoryzacją sesji, HTTP Range i `no-store`;
    - zoom, numer karty i strony, wyszukiwanie;
    - widok dzielony (dwie instancje);
    - skróty klawiszowe z briefu §6;
    - znak wodny przy pobraniu.
- **Kryteria akceptacji:**
    - [ ] dokument 200 stron otwiera się bez pobierania całości;
    - [ ] dwa dokumenty obok siebie;
    - [ ] test Playwright.
- **Zależności:** T-033. **Odwołania:** brief §4.3, §6.
- **[decyzja]** czy `pe:documentViewer` wystarcza (najpierw zrób krótki prototyp porównawczy).

### [ ] T-035 Notatki wewnętrzne i zaznaczenia
- **Cel:** praca zespołu sędziego bez mieszania z aktami jawnymi.
- **Zakres:**
    - notatki do sprawy;
    - zaznaczenia jako JSON ze współrzędnymi, rysowane jako nakładka;
    - widoczność tylko dla zespołu sędziego;
    - kopiowanie z cytatem (sygnatura, karta).
- **Kryteria akceptacji:**
    - [ ] notatki i zaznaczenia nie występują w eksporcie akt jawnych ani w widoku referenta.
- **Zależności:** T-034. **Odwołania:** FR-24.

### [ ] T-036 Anonimizacja i wgląd osób trzecich (UC-15, UC-16)
- **Cel:** zanonimizowane wersje dokumentów i wgląd w akta.
- **Zakres:**
    - generowanie zanonimizowanej kopii (redakcja po stronie serwera, z kontrolą człowieka);
    - flaga art. 80;
    - rejestr wniosków o wgląd;
    - widok osoby trzeciej z banerem.
- **Kryteria akceptacji:**
    - [ ] osoba trzecia nie otrzymuje oryginału (test API);
    - [ ] dokument z flagą art. 80 nie może zostać opublikowany bez wersji zanonimizowanej.
- **Zależności:** T-031, T-033. **Odwołania:** FR-22, FR-27, FR-29.

### [ ] T-037 Integracja z HSM (PKCS#11)
- **Cel:** klucze wyłącznie w HSM.
- **Zakres:**
    - konfiguracja SunPKCS11 (SoftHSM2 w dev);
    - opakowywanie kluczy plików;
    - odszyfrowanie wpływających PDF-ów zaszyfrowanych certyfikatem (Adobe.PubSec) w usłudze dokumentów;
    - podpis łańcucha audytu.
- **Kryteria akceptacji:**
    - [ ] żaden klucz prywatny nie występuje w plikach ani w bazie (skan repozytorium i bazy);
    - [ ] testy na SoftHSM2.
- **Zależności:** T-030, T-015. **Odwołania:** D12.

---

## Etap 4. Podpis i doręczenia

### [ ] T-040 Podpis kwalifikowany dwuetapowy (D10)
- **Cel:** sędzia podpisuje kartą przez aplikację lokalną.
- **Zakres:**
    - serwer: DSS `getDataToSign` → interfejs `LocalSignerClient` (przeglądarka ↔ aplikacja lokalna) → `signDocument` + znacznik czasu (PAdES-B-T) → walidacja;
    - kolejne podpisy w składzie 3 sędziów;
    - implementacja testowa podpisująca certyfikatem z SoftHSM2.
- **Kryteria akceptacji:**
    - [ ] podpisany dokument przechodzi walidację DSS;
    - [ ] status czynności „podpisana” i ruch procesu w jednej transakcji.
- **Zależności:** T-026, T-037. **Odwołania:** FR-16, D10.
- **[decyzja]** aplikacja lokalna: SDK dostawcy kart czy własna. **[zaślepka]** do tego czasu.

### [ ] T-041 Rejestr doręczeń (UC-08)
- **Cel:** dokładna data doręczenia uruchamia termin.
- **Zakres:**
    - ekspedycja (wybór adresata: pełnomocnik, gdy ustanowiony – BR-06);
    - książka nadawcza;
    - rejestracja zwrotki (doręczenie do rąk, zastępcze, awizo);
    - przeliczenie terminu (T-025) i aktualizacja timera.
- **Kryteria akceptacji:**
    - [ ] rejestracja doręczenia ustawia termin 7 dni;
    - [ ] korekta daty przelicza termin i timer.
- **Zależności:** T-025, T-020. **Odwołania:** FR-25, FR-26.

### [ ] T-042 Szkielet workerów integracji
- **Cel:** wspólna baza dla wszystkich integracji.
- **Zakres:**
    - moduł `workers` z `operaton-external-task-client`;
    - uwierzytelnienie client credentials w ID-users;
    - ponawianie z rosnącym odstępem, incydenty, klucze idempotencji;
    - metryki;
    - jeden JAR na temat lub jeden z konfiguracją tematów (uzasadnij w ADR).
- **Kryteria akceptacji:**
    - [ ] worker testowy przechodzi pełny cykl fetch-and-lock → complete / failure → incydent w Cockpicie.
- **Zależności:** T-020, T-004. **Odwołania:** D7.

### [ ] T-043 Adaptery doręczeń
- **Cel:** wysyłka elektroniczna i pocztowa.
- **Zakres:**
    - interfejsy i zaślepki WireMock dla e-Doręczeń / ePUAP i operatora pocztowego (Elektroniczny Nadawca);
    - mapowanie statusów na komunikaty procesu.
- **Kryteria akceptacji:**
    - [ ] testy kontraktowe na zaślepkach;
    - [ ] brak duplikatów wysyłki przy ponowieniu.
- **Zależności:** T-042, T-041. **Odwołania:** FR-25. **[zaślepka]** API zewnętrzne.

---

## Etap 5. Raporty, archiwum, eksploatacja

### [ ] T-050 Raporty i statystyki (UC-19)
- **Zakres:**
    - wpływ i załatwienia, odsetek nadania biegu, odsetek uwzględnionych zażaleń;
    - czas wstępnej kontroli (mediana, p90), zaległości, obciążenie;
    - eksport do XLSX i CSV.
- **Kryteria akceptacji:**
    - [ ] liczby zgodne z danymi testowymi (testy na zbiorze referencyjnym).
- **Zależności:** T-027, T-028. **Odwołania:** FR-32.

### [ ] T-051 Pisma ogólne (UC-17)
- **Zakres:** rejestr pism niebędących skargami, odpowiedź z szablonu (pouczenie o wymogach skargi), termin odpowiedzi.
- **Kryteria akceptacji:**
    - [ ] ścieżka „pismo ogólne” z T-021 działa z UI.
- **Zależności:** T-013, T-026. **Odwołania:** FR-30.

### [ ] T-052 Archiwizacja (UC-21)
- **Zakres:**
    - zamknięcie sprawy, kategoria archiwalna z konfiguracji;
    - paczka archiwalna (metadane XML + PDF/A), spis zdawczo-odbiorczy;
    - roczny eksport repertorium do PDF/A z sumą kontrolną.
- **Kryteria akceptacji:**
    - [ ] paczka przechodzi walidację;
    - [ ] Object Lock na zarchiwizowanych plikach.
- **Zależności:** T-030. **Odwołania:** FR-09, FR-34, NFR-12.

### [ ] T-053 Role Ansible dla VM
- **Cel:** produkcja i przedprodukcja odtwarzalne z kodu.
- **Zakres:**
    - role: WildFly 41 + dystrybucja Operaton (standalone-ha, 2 węzły), HAProxy, PostgreSQL 17 + Patroni + etcd, OpenSearch (3 węzły), usługi systemd dla docs-service i workerów;
    - inwentarze preprod i prod;
    - sekrety poza repozytorium.
- **Kryteria akceptacji:**
    - [ ] świeże VM (np. testowe w Vagrant lub w środowisku TK) stawiają się jednym playbookiem;
    - [ ] wersje pakietów zgodne z obrazami Docker.
- **Zależności:** T-006. **Odwołania:** D11, NFR-07.
- **[decyzja]** system operacyjny VM i sposób dystrybucji pakietów.

### [ ] T-054 Monitoring i logi
- **Zakres:**
    - metryki (Prometheus): JVM, pule połączeń, zadania i incydenty Operaton, kolejka OCR;
    - endpoint `/health`;
    - logi JSON do centralnego systemu;
    - alerty: incydenty, terminy bez czynności, błędy weryfikacji łańcucha audytu.
- **Kryteria akceptacji:**
    - [ ] panel Grafana z kluczowymi wskaźnikami;
    - [ ] alert testowy dochodzi.
- **Zależności:** T-042, T-053. **Odwołania:** NFR-07.

### [ ] T-055 Utwardzenie bezpieczeństwa
- **Zakres:**
    - przegląd OWASP ASVS (poziom 2) dla aplikacji;
    - CSP bez wyjątków, limity uploadu i weryfikacja sygnatur plików;
    - ochrona przed path traversal i open redirect;
    - przegląd uprawnień bazy;
    - przygotowanie do testu penetracyjnego;
    - dane wejściowe do DPIA.
- **Kryteria akceptacji:**
    - [ ] lista kontrolna ASVS w `docs/security/` z wynikami;
    - [ ] brak znalezisk wysokich w skanach.
- **Zależności:** etapy 1–4. **Odwołania:** NFR-04, NFR-05, NFR-13.

### [ ] T-056 Aktualizacja Operaton (procedura)
- **Zakres:**
    - procedura i skrypt aktualizacji wersji Operaton (cykl 6-miesięczny);
    - test migracji schematu na kopii danych;
    - checklist zgodności WildFly.
- **Kryteria akceptacji:**
    - [ ] próbna aktualizacja na środowisku testowym opisana w `docs/runbooks/`.
- **Zależności:** T-053. **Odwołania:** architektura §10.

---

## Notatki z realizacji

Po każdym zadaniu dopisz tu jedną linię w formacie: `T-xxx – data – co zrobiono, co zostało, link do ADR`.
