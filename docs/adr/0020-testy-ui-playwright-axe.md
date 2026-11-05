# ADR 0020 – Testy UI w Playwright z audytem axe-core, poza `mvn verify`

Status: propozycja

## Decyzja
Testy ekranów krytycznych (rejestracja wpływu, podgląd akt, podpis) i audyt dostępności (axe-core, WCAG 2.1 AA) są w `ui-tests/`
(Playwright, TypeScript) i uruchamiane przeciw działającej aplikacji (tryb demo albo mock OIDC). Nie wchodzą do `mvn verify`, bo wymagają WildFly.
W CI uruchamiane po wdrożeniu na środowisko testowe (osobny job, do włączenia po pierwszym wdrożeniu).

## Konsekwencje
- Test braku tokenu w przeglądarce (T-004) wymaga mock OIDC – oznaczony `skip` bez `ESKARGA_OIDC_TEST`.
- Statyczna kontrola stron w sesji bez serwera: `xmllint` (poprawność XML) i własny walidator EL (zgodność wyrażeń z beanami).

## Dopisek 05.11.2026 – projekt `oidc`: procesy od końca do końca przez ID-users (T-058)
Konfiguracja Playwright ma dwa projekty: `demo` (dotychczasowe ekrany w trybie demo i axe) oraz `oidc` (`ui-tests/tests/e2e`), który
na środowisku dev obok ID-users przechodzi łańcuch procesów: logowanie w Keycloaku z kodem jednorazowym (TOTP liczony w teście,
RFC 6238 – jak w e2e projektu id), kontrola braku tokenów w przeglądarce (T-004), rejestracja pisma ze skanem PDF (S-03), kolejka
z podglądem skanu w pdf.js i kwalifikacja (S-06), kreator założenia sprawy z oknem potwierdzenia i sygnaturą z licznika (S-07, BR-01),
akta z podglądem dokumentu (S-14), repertorium (S-08), dziennik wpływu (S-04), przełącznik roli, wylogowanie kończące sesję SSO.
Kroki są zależne (numer DW, sygnatura), więc działają po kolei w jednym kontekście przeglądarki (`test.describe.serial`).
Konto testowe zakłada test przez Admin API Keycloaka (`ESKARGA_E2E_KC_ADMIN_HASLO`) z rolami klienta `eskarga` i akcją `CONFIGURE_TOTP`,
albo używa istniejącego konta (`ESKARGA_E2E_LOGIN`, `ESKARGA_E2E_HASLO`, `ESKARGA_E2E_TOTP`). Sekrety wyłącznie w zmiennych środowiskowych
(Windows: `infra\windows\testy-gui.cmd` czyta je z `id\dev\.env`). Testy tworzą prawdziwe dane – tylko środowisko dev/test.
Wybrano Playwright (nie Selenium): już używany w projekcie i w ID-users, wbudowane czekanie na stan strony (PrimeFaces AJAX), ślady i zrzuty
z nieudanych kroków, ten sam język co e2e ID-users.

