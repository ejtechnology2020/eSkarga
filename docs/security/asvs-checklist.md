# Lista kontrolna OWASP ASVS 4.0.3 (poziom 2) – eSkarga (T-055)

Stan na 03.11.2026. Oznaczenia: ✅ spełnione w kodzie/konfiguracji, 🔶 częściowo / do potwierdzenia na środowisku, ⬜ do zrobienia, — nie dotyczy.
Wynik skanu zależności: `mvn -B verify -Psecurity` (CVSS ≥ 7 przerywa build; wymaga klucza NVD w CI).

| Rozdział ASVS | Wymaganie (skrót) | Stan | Dowód / miejsce |
| --- | --- | --- | --- |
| V1.1 Architektura | Model zagrożeń, granice zaufania, zasady | ✅ | `doc/architektura-ts-tw-decyzje.md`, D1–D13, ADR 0001–0020 |
| V1.4 Kontrola dostępu | Jeden punkt decyzji o dostępie | ✅ | `CaseAccessPolicy` + `CaseAccessService`, `ScreenAccessFilter`, filtr wyników wyszukiwania |
| V2.1 Hasła | Aplikacja nie przechowuje haseł użytkowników | ✅ | OIDC ID-users (D3); brak tabel z hasłami |
| V2.8 MFA | Wymuszenie drugiego składnika | ✅ | `MfaFilter` (claim `acr`/`amr` konfigurowalny), S-26 |
| V3.2 Sesja | Nowa sesja po logowaniu, unieważnienie przy wylogowaniu | ✅ | Jakarta Security + `LogoutBean`, back-channel logout (`BackChannelLogoutServlet`, `SessionRegistry`) |
| V3.4 Ciasteczka | `HttpOnly`, `Secure`, `SameSite=Strict` | ✅ | `web.xml` cookie-config, `SecurityHeadersFilter` |
| V3.5 Tokeny | Tokeny nie trafiają do przeglądarki | ✅ | sesja po stronie serwera; test Playwright `ui-tests/tests/tokeny.spec.ts` (do uruchomienia) |
| V4.1 Kontrola dostępu | Weryfikacja po stronie serwera dla każdego żądania | ✅ | filtry + `canRead` w `DocumentServlet`, API z JWT (`BearerTokenFilter`) |
| V4.2 Operacje | Brak IDOR – identyfikatory sprawdzane z przydziałem | ✅ | `DocumentServlet`, `NoteService`, `TaskBoardService` |
| V4.3 Administracja | Odmowy w dzienniku zdarzeń | ✅ | `AuditService.recordDenial` (REQUIRES_NEW, ADR 0014) |
| V5.1 Walidacja wejścia | Walidacja po stronie serwera, lista dozwolonych | ✅ | Bean Validation w formularzach, `ProcessVariables.validate`, enumy |
| V5.2 Sanityzacja | Brak eval; szablony w piaskownicy | ✅ | FreeMarker `ALLOWS_NOTHING_RESOLVER`, `api_builtin_enabled=false`, auto-escape (D13); pdf.js `isEvalSupported=false` |
| V5.3 Kodowanie wyjścia | Escapowanie w widokach | ✅ | JSF/EL escapuje domyślnie; `h:outputText escape="false"` nie występuje poza komponentem `tk:` z tekstem systemowym |
| V5.3.3 XSS | CSP bez `unsafe-eval`/`unsafe-inline` dla skryptów | ✅ | `primefaces.CSP=true`, polityka w `web.xml` (`script-src 'self'`) |
| V6.2 Kryptografia | Algorytmy zatwierdzone, klucze w HSM | ✅ | SHA-256 (audyt, dokumenty), RSA/PAdES przez DSS, PKCS#11 (`HsmKeyStore`, pkcs12 zabronione w PROD) |
| V7.1 Logowanie | Zdarzenia bez danych wrażliwych, z tożsamością | ✅ | `audit_event` (łańcuch skrótów), logi JSON WildFly |
| V7.4 Obsługa błędów | Strony błędów bez szczegółów technicznych | ✅ | `blad/403,404,500.xhtml`, `ApiExceptionMapper` |
| V8.1 Ochrona danych | Dane wrażliwe nie w URL, `no-store` | ✅ | identyfikatory w URL, `Cache-Control: no-store` dla dokumentów i API |
| V8.3 Dane osobowe | Minimalizacja – zmienne procesu bez danych osobowych | ✅ | `ProcessVariablesTest` (D8); dokumenty niejawne tylko metadane (NFR-06) |
| V9.1 Komunikacja | TLS 1.2+ od brzegu, HSTS | ✅ | HAProxy (`ssl-min-ver TLSv1.2`), `SecurityHeadersFilter` HSTS |
| V10.3 Integralność | Artefakty z sumami kontrolnymi, zależności skanowane | ✅ | CI `SHA256SUMS`, Dependency-Check, Ansible `checksum` |
| V11.1 Logika biznesowa | Reguły BR-xx wymuszane po stronie serwera | ✅ | testy BR-01…BR-10 w `domain/service` |
| V12.1 Pliki | Limit rozmiaru i weryfikacja sygnatury pliku | ✅ | `CaseDocumentService.upload` (`%PDF-`), limit uploadu w `web.xml` (`max-file-size`) |
| V12.3 Pliki | Brak path traversal | ✅ | dostęp wyłącznie po identyfikatorach liczbowych; klucze S3 generowane przez serwer |
| V12.4 Pliki | Pliki poza korzeniem WWW, bez wykonywania | ✅ | S3 z Object Lock, serwowanie przez `DocumentServlet` z `nosniff` |
| V13.1 API | Uwierzytelnienie każdego wywołania, JSON bez XXE | ✅ | JWT RS256 (`JwtRs256Verifier`), JSON-P (bez XML) |
| V13.2 REST | Ograniczenie metod, brak CSRF przez tokeny | ✅ | JAX-RS metody jawne; API tylko Bearer; JSF ViewState chroni formularze |
| V14.2 Zależności | Aktualne komponenty, bez niepotrzebnych | 🔶 | Dependency-Check w CI; przegląd po pierwszym pełnym skanie z kluczem NVD |
| V14.4 Nagłówki HTTP | CSP, X-Content-Type-Options, Referrer-Policy, X-Frame-Options | ✅ | `SecurityHeadersFilter`, `frame-ancestors 'none'` |
| V14.5 Walidacja żądań | Open redirect | ✅ | `SafeRedirect` (tylko ścieżki względne w obrębie aplikacji) + test |

## Do testu penetracyjnego

- Zakres: aplikacja `/eskarga`, API `/eskarga/api`, usługa dokumentów `/eskarga-docs/api`, workery (sieć wewnętrzna).
- Konta testowe w mock OIDC (`infra/docker/oidc`), dane wyłącznie fikcyjne.
- Znane ograniczenia: kontrakt ID-users nieznany (claimy konfigurowalne), HSM sieciowy do zakupu (SoftHSM2 w dev).
