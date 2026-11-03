# ADR 0002 – Mechanizm OIDC (Jakarta Security) i tryb demo interfejsu

- Status: propozycja do potwierdzenia z zespołem ID-users (T-004 [zaślepka])
- Data: 3.11.2026

## Decyzja

1. Logowanie przez `@OpenIdAuthenticationMechanismDefinition` (Jakarta Security 4, WildFly 41) zamiast
   `elytron-oidc-client`. Powody: konfiguracja w kodzie przez EL (`#{oidcSettings.*}`) czytana z system properties,
   ten sam WAR na wszystkich środowiskach (D11), brak edycji `standalone.xml` poza źródłem danych.
2. Nazwy claimów nie są zgadywane. Konfigurowalne: issuer, client id/secret, claim ról (`eskarga.oidc.rolesClaim`),
   claim grup, claim nazwy użytkownika, claim MFA (`acr` lub `amr`) i wymagana wartość. Do potwierdzenia z ID-users:
   - adres discovery i JWKS,
   - czy role przychodzą w `roles`, `groups` czy `realm_access.roles`,
   - jak sygnalizowane jest MFA (`acr=mfa`? `amr` z `otp`/`hwk`?),
   - back-channel logout i API użytkowników (tylko odczyt) do `ReadOnlyIdentityProvider` Operatona.
3. Tryb `eskarga.security.mode=demo` wyłącza mechanizm OIDC (rozszerzenie CDI `SecurityModeExtension`) i daje tożsamość
   z danych przykładowych; służy wyłącznie do pracy nad interfejsem na stacji deweloperskiej. Na test i prod obowiązuje
   `oidc`; brak MFA ma kończyć się odmową (ekran S-26 „Wymagane logowanie z drugim składnikiem”).

## Konsekwencje

- Test integracyjny „bez MFA → 403, z MFA → dostęp” wymaga mock OIDC z konfigurowalnym `acr` (compose: `mock-oauth2-server`).
- Filtr MFA i interceptor `identityService.setAuthentication(userId, groups)` dla Operatona powstają w T-004 po poznaniu kontraktu.

## Uzupełnienie (03.11.2026, T-004)

- **MFA:** `MfaFilter` sprawdza claim tokenu ID (`eskarga.oidc.mfaClaim`, domyślnie `acr`) względem listy wartości
  (`eskarga.oidc.mfaRequiredValue`, np. `urn:tk:acr:mfa,mfa`); dla `amr` wystarczy obecność metody na liście. Brak MFA → 403
  i ekran S-26 oraz zdarzenie odmowy w dzienniku. Pusta wymagana wartość = MFA wymusza sam ID-users.
- **Operaton:** `OperatonAuthenticationFilter` ustawia na czas żądania `identityService.setAuthentication(login, grupy)` – grupy
  to grupy kandydatów z ról (`TaskBoardService.CANDIDATE_GROUPS`).
- **Back-channel logout:** `POST /oidc/backchannel-logout` z `logout_token` (JWT podpisany kluczem wydawcy, `aud` = client id,
  zdarzenie `backchannel-logout`, `sid`/`sub`); `SessionRegistry` unieważnia sesje. Adres do zarejestrowania w ID-users.
- **Usługi techniczne:** tokeny client credentials (`ClientCredentialsTokenClient`) dla aplikacji → usługa dokumentów
  (rola `eskarga-docs`) i workerów → API aplikacji (rola `eskarga-worker`); weryfikacja RS256 kluczami JWKS wydawcy.
- Nazwy claimów, ról technicznych i adresy pozostają konfiguracją – do potwierdzenia z zespołem ID-users.
