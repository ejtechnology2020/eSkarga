# Testy UI (Playwright + axe-core) – ADR 0020

Dwa projekty Playwright (`playwright.config.ts`), oba przeciw działającej aplikacji (`ESKARGA_URL`, domyślnie `http://localhost:8082/eskarga`):

| Projekt | Co sprawdza | Wymaga |
| --- | --- | --- |
| `widget` | widżet PDF (`tk:pdfViewer` + `pdf-viewer.js`) w Chromium pod CSP z `web.xml`: skan osadzony w stronie i wstawiony po AJAX PrimeFaces; serwer testowy w teście, pdf.js z npm w wersji webjara | nic (działa też w CI, zadanie `widget`) |
| `demo` | ekrany w trybie demo (`?rola=`, `?stan=`), audyt axe (WCAG 2.1 A/AA) | aplikacja z `ESKARGA_SECURITY_MODE=demo` |
| `oidc` | procesy od końca do końca przez ID-users: logowanie z kodem jednorazowym (TOTP), brak tokenów w przeglądarce, rejestracja pisma ze skanem PDF (S-03), kolejka z podglądem skanu i kwalifikacja (S-06), założenie sprawy z sygnaturą (S-07, BR-01), akta (S-14), repertorium (S-08), dziennik wpływu (S-04), przełącznik roli, wylogowanie | środowisko dev obok ID-users (Keycloak :8180), `ESKARGA_E2E=1` |

```bash
cd ui-tests && npm ci && npx playwright install chromium
npm run test:widget                                          # widżet PDF bez aplikacji (ESKARGA_CHROMIUM=… gdy Chromium już jest)
npm run test:demo                                            # tryb demo + axe
ESKARGA_E2E=1 ESKARGA_E2E_KC_ADMIN_HASLO=<KEYCLOAK_ADMIN_PASSWORD z id/dev/.env> npm run test:oidc   # zakłada konto e2e.eskarga i konfiguruje OTP
ESKARGA_E2E=1 ESKARGA_E2E_LOGIN=admin.wszystko ESKARGA_E2E_HASLO=… ESKARGA_E2E_TOTP=<klucz Base32> npm run test:oidc   # istniejące konto
npx playwright show-report                                   # raport HTML, ślady i zrzuty nieudanych kroków
```

Windows: `infra\windows\testy-gui.cmd` (czyta hasła z `..\id\dev\.env`; `-Demo`, `-Login <konto> -KluczTotp <Base32>`, `-Przegladarka`).

Zmienne projektu `oidc` są opisane w `tests/e2e/srodowisko.ts`. Konto zakładane przez test (`e2e.eskarga`, fikcyjne dane) dostaje role klienta
`eskarga` z katalogu ID-users (`TK_BIURO_PODAWCZE`, `TK_REFERENT`, `TK_ASYSTENT`, `TK_ADMIN`) i akcję `CONFIGURE_TOTP`; klucz TOTP z ekranu
Keycloaka test trzyma tylko w pamięci. Dla istniejącego konta klucz podaje się w `ESKARGA_E2E_TOTP` albo w pliku `totp-<login>.txt`
w `ID_USERS_KLUCZE_DIR` (zapisuje go narzędzie `npm --prefix frontend run aplikacje:eskarga` projektu id). Hasła i klucze nigdy nie trafiają do repozytorium.

Testy nie są częścią `mvn verify` (wymagają WildFly i ID-users); kroki projektu `oidc` wykonują się po kolei w jednym kontekście przeglądarki
i tworzą prawdziwe dane (pismo DW, sprawa Ts) – uruchamiaj je tylko na środowisku deweloperskim lub testowym.
