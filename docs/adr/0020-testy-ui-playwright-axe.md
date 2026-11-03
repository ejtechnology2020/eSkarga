# ADR 0020 – Testy UI w Playwright z audytem axe-core, poza `mvn verify`

Status: propozycja

## Decyzja
Testy ekranów krytycznych (rejestracja wpływu, podgląd akt, podpis) i audyt dostępności (axe-core, WCAG 2.1 AA) są w `ui-tests/`
(Playwright, TypeScript) i uruchamiane przeciw działającej aplikacji (tryb demo albo mock OIDC). Nie wchodzą do `mvn verify`, bo wymagają WildFly.
W CI uruchamiane po wdrożeniu na środowisko testowe (osobny job, do włączenia po pierwszym wdrożeniu).

## Konsekwencje
- Test braku tokenu w przeglądarce (T-004) wymaga mock OIDC – oznaczony `skip` bez `ESKARGA_OIDC_TEST`.
- Statyczna kontrola stron w sesji bez serwera: `xmllint` (poprawność XML) i własny walidator EL (zgodność wyrażeń z beanami).
