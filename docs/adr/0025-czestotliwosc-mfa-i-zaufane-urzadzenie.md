# ADR 0025 – Częstotliwość drugiego składnika (OTP) i „zaufane urządzenie”

- Status: proponowane 05.11.2026 (do decyzji zamawiającego: wartości czasów); realizacja po stronie ID-users (realm jako kod)
- Powiązane: ADR 0002 (Elytron OIDC, `acr`), ADR 0024; ID-users: `id-users-keycloak-config/src/main/realm/id-users.json`, ADR-0015 (MFA)

## Kontekst

Użytkownicy eSkargi podają kod jednorazowy praktycznie przy każdym wejściu do aplikacji. Nie wynika to z eSkargi (`MfaFilter`
sprawdza tylko `acr` w tokenie, klient `eskarga` wymaga poziomu `mfa` przez `default.acr.values`), lecz z ustawień realmu
`id-users` sprawdzonych 05.11.2026:

| Ustawienie realmu | Wartość | Skutek dla eSkargi |
| --- | --- | --- |
| ważność poziomu LoA2 (`idusers-loa2.loa-max-age`) | 30 min | po 30 min od wpisania kodu każde logowanie do eSkargi znów prosi o kod |
| sesja SSO: bezczynność / maksymalnie | 30 min / 10 h | po 30 min bez aktywności pełne logowanie (hasło i kod) |
| „zapamiętaj mnie” (`rememberMe`) | wyłączone | zamknięcie przeglądarki kończy sesję |
| sesja eSkargi (`web.xml`) | 30 min | po wygaśnięciu powrót do ID-users; przy ważnej sesji SSO i LoA2 bez pytań |

Keycloak 26 nie ma wbudowanej funkcji „zaufane urządzenie” (pominięcie OTP na znanej przeglądarce przez N dni).
Poziom zabezpieczeń odniesienia: NIST SP 800-63B AAL2 – pełne MFA co najmniej raz na 12 godzin oraz ponowne
uwierzytelnienie po 30 minutach bezczynności (może być jednoskładnikowe).

## Rozważane warianty

1. **Kod raz dziennie** – ważność LoA2 równa maksymalnej długości sesji SSO (10 h), dłuższy czas bezczynności sesji SSO
   (np. 2 h), opcjonalnie `rememberMe`. Tylko konfiguracja realmu, bez kodu. Mieści się w AAL2.
2. **Passkey zamiast kodu** – WebAuthn (Windows Hello, YubiKey) jako alternatywa dla OTP na poziomie LoA2 (ID-users ma już
   WebAuthn na LoA3). Jedno dotknięcie zamiast przepisywania kodu, odporność na phishing. Tylko konfiguracja przepływu.
3. **Zaufane urządzenie na N dni** – własny authenticator w `id-users-keycloak-ext` (podpisane ciasteczko urządzenia
   powiązane z kontem, lista urządzeń w konsoli ID-users, unieważnianie). Zmiana architektury ID-users (ADR tam), wykracza
   poza AAL2 – nieadekwatne dla systemu sądowego jako pierwszy krok.
4. **MFA tylko na ekranach wrażliwych eSkargi** (podpis, administracja) z podbiciem poziomu na żądanie – mechanizm Elytron
   OIDC nie wysyła `acr_values`, wymagałoby obejścia; odrzucone.

## Decyzja (propozycja)

- Wariant 1 jako pierwszy krok, wariant 2 jako uzupełnienie. Proponowane wartości realmu `id-users`: `idusers-loa2.loa-max-age`
  = 36000 s (10 h), `ssoSessionIdleTimeout` = 7200 s (2 h), `ssoSessionMaxLifespan` = 36000 s (10 h); `rememberMe` bez zmian
  (wyłączone) do osobnej decyzji. Wariant 3 odłożony; jeśli wróci, wymaga ADR w ID-users i przeglądu bezpieczeństwa.
- eSkarga bez zmian: `default.acr.values=mfa` klienta i `MfaFilter` (`acr` ∈ {mfa, wysoki}) zostają; sesja eSkargi 30 min
  (po jej wygaśnięciu logowanie przechodzi bez pytań, dopóki sesja SSO i LoA2 są ważne).
- Realizacja: zmiana w `id-users-keycloak-config` na osobnej gałęzi projektu id (realm jako kod, idempotentna) z ADR po
  stronie ID-users; po wdrożeniu `dev/keycloak/apply-realm.sh` i test: kod raz na dzień pracy, po 2 h bezczynności hasło i kod.

## Konsekwencje

- Do czasu decyzji użytkownicy dev podają kod co 30 minut.
- Po zmianie sesja SSO trwa dłużej, więc rośnie znaczenie blokady ekranu stacji i wylogowania (RP-initiated logout eSkargi
  kończy sesję SSO w ID-users – ADR 0002).
