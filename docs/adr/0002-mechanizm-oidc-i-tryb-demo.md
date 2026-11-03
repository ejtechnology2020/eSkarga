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


## Kontrakt ID-users (03.11.2026) – wartości przyjęte w konfiguracji

| Element | Wartość | Gdzie |
| --- | --- | --- |
| Issuer dev / test | `http://localhost:8180/realms/id-users` / `https://login.test.id-users.example.com/realms/id-users` (ostateczny po uruchomieniu TEST) | `eskarga.oidc.issuer` |
| Discovery / JWKS | `<issuer>/.well-known/openid-configuration`, `<issuer>/protocol/openid-connect/certs` | `JwtRs256Verifier.fromSettings` |
| Klient WWW | poufny, authorization code + PKCE S256, rejestracja w portalu integratora (PU-INT-01) | `OidcSecurityConfig` |
| Role aplikacji | `resource_access.<client_id>.roles` (zakres `roles`); role realmu `realm_access.roles`; grupy nie trafiają do tokenów | `RoleMapper`, `eskarga.oidc.rolesClaim` |
| MFA | `acr`: `haslo` (LoA 1), `mfa` (LoA 2), `wysoki` (LoA 3); `amr` nie jest wystawiany; żądanie `acr_values=mfa` | `MfaPolicy`, `eskarga.oidc.mfaRequiredValue=mfa,wysoki`, `extraParameters` |
| Usługi techniczne | `client_credentials`, konto serwisowe; `client_secret` albo zalecane `private_key_jwt`; token 5 min, bez refresh | `ClientCredentialsTokenClient` (`.keyStore` → `private_key_jwt`) |
| Back-channel logout | OIDC Back-Channel Logout 1.0, `sid` wymagane, adres podawany przy rejestracji | `BackChannelLogoutServlet`, `SessionRegistry` |
| Walidacja tokenu | `iss`, podpis JWKS, `exp`, `aud`/`azp`; `sub` jako stały identyfikator | `JwtRs256Verifier` |

Dev: Keycloak 26 z realmem `id-users` (`infra/docker/keycloak/id-users-realm.json`) odtwarza ten kontrakt lokalnie.

## Adres lokalny (decyzja 03.11.2026)

Testy lokalne na `http://localhost:8180/realms/id-users` (domyślna wartość `eskarga.oidc.issuer`). Ponieważ kontener aplikacji nie
dosięga `localhost` hosta, discovery/token/JWKS idą pod `eskarga.oidc.providerUri` (w Compose `http://oidc:8180/realms/id-users`,
Keycloak z `--hostname-backchannel-dynamic=true`; przy Keycloaku na hoście `http://host.docker.internal:8180/...`), a `iss` tokenów
i adresy w przeglądarce pozostają `localhost:8180`.
