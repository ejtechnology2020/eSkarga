# ADR 0001 – Stos technologiczny i decyzje architektoniczne D1–D13

- Status: zatwierdzone 3.11.2026 (zamawiający), spisane 3.11.2026
- Źródło: „Architektura rozwiązania – system repertoryjno-biurowy Ts/Tw (Operaton, ID-users)”

## Kontekst

System repertoryjno-biurowy Trybunału Konstytucyjnego obsługuje skargi (Ts) i wnioski (Tw) na etapie
wstępnej kontroli (art. 61 ustawy o TK). Wymagania: niezmienność repertorium (NFR-01), rozliczalność każdej
czynności (NFR-02), terminy liczone przez człowieka z pomocą systemu (D6), brak danych osobowych w silniku
procesów (RODO), logowanie przez wewnętrznego dostawcę tożsamości ID-users.

## Decyzje

| Nr | Decyzja | Skutek w repozytorium |
| --- | --- | --- |
| D1 | Operaton osadzony w aplikacji, wspólna baza i transakcja JTA z repertorium | `DocketNumberService` i `PreliminaryReviewProcess` działają w transakcji wywołującego (`MANDATORY`); jedno XA źródło danych `EskargaDS` |
| D2 | Frontend JSF/CDI/EJB + PrimeFaces 15 (`jakarta`) | moduł `app` (WAR), Facelets w `src/main/webapp`, komponenty kompozytowe `tk:*` |
| D3 | ID-users jako dostawca OIDC (Authorization Code + PKCE), klient poufny, tokeny tylko na serwerze | `security/OidcSecurityConfig` z konfigurowalnymi claimami (`OidcSettings`); ciasteczko `HttpOnly`, `Secure`, `SameSite=Strict` |
| D4 | Role ogólne z ID-users; przydziały do spraw i upoważnienia w aplikacji | `CaseAccessPolicy`, tabela `delegation`, `ScreenAccessFilter` |
| D5 | Dokumenty w repozytorium S3 (Object Lock), metadane i SHA-256 w bazie | `documents/DocumentStore`, `S3DocumentStore`, `DocumentServlet` (Range, `no-store`) |
| D6 | Terminy liczy domena; timery BPMN tylko przypominają | `DeadlineCalculator`, timery `${terminBrakow}` / `${terminZazalenia}` zamiast `P7D` |
| D7 | Integracje jako external tasks obsługiwane przez osobne workery | moduł `workers`, temat `przekazanie-merytoryczne` w modelu, klucz idempotencji |
| D8 | Zmienne procesu bez danych osobowych | `ProcessVariables` z listą dozwolonych nazw i test `ProcessVariablesTest` |
| D9 | WildFly 41 z dystrybucją Operaton (silnik współdzielony), aplikacja jako process application | `EskargaProcessApplication` (EJB), `META-INF/processes.xml`, `jboss-deployment-structure.xml` |
| D10 | Podpis kwalifikowany kartą przez aplikację lokalną, podpis dwuetapowy (DSS) | ekran S-16 (kroki, błędy), interfejs `LocalSignerClient` planowany w T-040 |
| D11 | Produkcja na VM (Ansible), dev i test w Dockerze, jeden WAR wszędzie | `infra/docker/compose.yml`, konfiguracja przez system properties / zmienne środowiskowe |
| D12 | Nowe repozytorium S3 z Object Lock i para HSM (PKCS#11) | dev: SeaweedFS i SoftHSM2 w Compose; kod pod API S3 i PKCS#11 bez zależności od dostawcy |
| D13 | Pisma z szablonów: FreeMarker 2.3.x (XHTML) → OpenHTMLtoPDF → PDF/A-2b → veraPDF, w usłudze dokumentów | `docs-service`: `FreemarkerTemplateRenderer` w piaskownicy (test `FreemarkerSandboxTest`), `PdfRenderer`, `PdfAValidator` |

## Konsekwencje

- Nie wprowadzamy Spring, Quarkus, Angular, React ani Lomboka (Maven Enforcer blokuje deklaracje wprost).
- Silnik Operaton sam zależy od `spring-beans`; to zależność silnika, nie frameworka aplikacji.
- Każda reguła BR-xx ma test jednostkowy (`DocketNumberAllocatorTest`, `DeadlineCalculatorTest`,
  `CaseAccessPolicyTest`), a każda ścieżka BPMN test procesowy (`WstepnaKontrolaProcessTest`).
