# Testy UI (Playwright + axe-core) – ADR 0020

```bash
cd ui-tests && npm ci && npx playwright install chromium
ESKARGA_URL=http://localhost:8080/eskarga npm test          # aplikacja w trybie demo
ESKARGA_OIDC_TEST=1 npm test                                 # z mock OIDC (test braku tokenu w przeglądarce)
```
Testy nie są częścią `mvn verify` (wymagają działającej aplikacji na WildFly); w CI uruchamiane po wdrożeniu na środowisko testowe.
