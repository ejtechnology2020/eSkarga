# ADR 0002 – Mechanizm OIDC (Elytron OIDC, wcześniej Jakarta Security) i tryb demo interfejsu

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
| Klient WWW | poufny, authorization code + PKCE S256 (kontrakt); **otwarte 05.11.2026**: mechanizm Jakarta Security (Soteria na WildFly 41) nie wysyła `code_challenge` – patrz uzupełnienie; rejestracja w portalu integratora (PU-INT-01) | `OidcSecurityConfig`, `zarejestruj-w-id-users.sh` (`ESKARGA_PKCE`) |
| Role aplikacji | `resource_access.<client_id>.roles` (zakres `roles`); role realmu `realm_access.roles`; grupy nie trafiają do tokenów | `RoleMapper`, `eskarga.oidc.rolesClaim` |
| MFA | `acr`: `haslo` (LoA 1), `mfa` (LoA 2), `wysoki` (LoA 3); `amr` nie jest wystawiany; żądanie `acr_values=mfa` | `MfaPolicy`, `eskarga.oidc.mfaRequiredValue=mfa,wysoki`, `extraParameters` |
| Uwierzytelnienie klienta WWW | rejestracja narzędziem ID-users (`aplikacje:eskarga`, T-037 projektu id) nadaje `private_key_jwt` (Keycloak `client-jwt`, klucz publiczny JWK w ID-users, prywatny PKCS#8 w katalogu wyników narzędzia); klient utworzony skryptem dev ma sekret | `oidc.json` `credentials.provider` = `${env.ESKARGA_OIDC_CLIENTAUTH}` (`jwt`/`secret`), `entrypoint.sh` buduje PKCS#12 z PEM, `ID_USERS_KLUCZE_DIR` |
| Usługi techniczne | `client_credentials`, konto serwisowe; `client_secret` albo zalecane `private_key_jwt`; token 5 min, bez refresh | `ClientCredentialsTokenClient` (`.privateKeyPem` + `kid` z `<klient>.kid` albo `.keyStore` → `private_key_jwt`) |
| Back-channel logout | OIDC Back-Channel Logout 1.0, `sid` wymagane, adres podawany przy rejestracji | `BackChannelLogoutServlet`, `SessionRegistry` |
| Walidacja tokenu | `iss`, podpis JWKS, `exp`, `aud`/`azp`; `sub` jako stały identyfikator | `JwtRs256Verifier` |

Dev: Keycloak 26 z realmem `id-users` (`infra/docker/keycloak/id-users-realm.json`) odtwarza ten kontrakt lokalnie.

## Adres lokalny (decyzja 03.11.2026)

Testy lokalne na `http://localhost:8180/realms/id-users` (domyślna wartość `eskarga.oidc.issuer`). Ponieważ kontener aplikacji nie
dosięga `localhost` hosta, discovery/token/JWKS idą pod `eskarga.oidc.providerUri` (w Compose `http://oidc:8180/realms/id-users`,
Keycloak z `--hostname-backchannel-dynamic=true`; przy Keycloaku na hoście `http://host.docker.internal:8180/...`), a `iss` tokenów
i adresy w przeglądarce pozostają `localhost:8180`.

## Środowisko dev obok ID-users (04.11.2026)

Compose eSkargi (`name: eskarga`) nie koliduje z Compose ID-users (`id-users`): aplikacja na `http://localhost:8082/eskarga`,
PostgreSQL 5433, RustFS (S3) 9100/9101, pozostałe porty w `.env` (`PORT_*`). Logowanie przez Keycloak ID-users z projektu `id`
(issuer `http://localhost:8180/realms/id-users`, discovery z kontenera przez `host.docker.internal`). Klientów `eskarga`
i `eskarga-workers` rejestruje w realmie `id-users` skrypt `infra/docker/keycloak/zarejestruj-w-id-users.sh` (usługa
`id-users-rejestracja`), który nadaje też role eSkargi fikcyjnym użytkownikom testowym ID-users. Adres back-channel
rejestrowany w ID-users: `http://host.docker.internal:8082/eskarga/oidc/backchannel-logout` (Keycloak ID-users ma wpis host.docker.internal).

## Uzupełnienie 05.11.2026 – pierwsze logowanie na WildFly 41 (wariant 1 wybrany, ale niewykonalny – patrz niżej)

- `@OpenIdAuthenticationMechanismDefinition` realizuje Soteria (Jakarta Security), nie podsystem `elytron-oidc-client`; logowanie
  na stronach wymusza `LoginFilter` przez `HttpServletRequest.authenticate` (strony bez `security-constraint`, bo tryb demo działa
  bez IdP). W adnotacji działają tylko wyrażenia `${…}`; bez `prompt=none`.
- Keycloak ID-users odrzuca logowanie: „Missing parameter: code_challenge_method” – klient `eskarga` wymaga PKCE S256 (kontrakt),
  a Soteria nie wysyła `code_challenge`. Sprawdzone w źródłach (05.11.2026): API Jakarta Security 4.0
  (`OpenIdAuthenticationMechanismDefinition`) nie ma atrybutu PKCE, `AuthenticationController` Soterii buduje żądanie autoryzacji
  bez `code_challenge`, w issue Soterii i specyfikacji brak zgłoszeń PKCE. Warianty:
  1. **Wyłączyć wymóg PKCE** dla klienta poufnego (`ESKARGA_PKCE=` w `.env`, ponowna rejestracja `rejestracja.cmd`). Kod
     autoryzacyjny chroni sekret klienta i walidacja `redirect_uri`; PKCE dla klientów poufnych jest w OAuth 2.0 zalecane,
     nie wymagane (wymagane w OAuth 2.1). Odstępstwo od kontraktu do uzgodnienia z zespołem ID-users.
  2. **Mechanizm Elytron OIDC** (`oidc.json`/`auth-method OIDC`, podsystem `elytron-oidc-client`, PKCE S256 wspierane) zamiast
     Jakarta Security – zmiana odczytu tokenów (`CurrentUser`, `MfaFilter`: `OpenIdContext` → `OidcSecurityContext`), wylogowania
     i konfiguracji przez EL.
- **Decyzja (05.11.2026): wariant 1 w dev** – `ESKARGA_PKCE=` w `infra/docker/.env` i ponowna rejestracja; wariant 2 (Elytron OIDC
  z PKCE S256) do zaplanowania przed środowiskiem TEST, gdzie kontrakt ID-users będzie egzekwowany. Domyślna wartość w
  `.env.example` pozostaje `S256`;
  `/callback` z błędem dostawcy pokazuje stronę „Logowanie nie powiodło się” zamiast 404 (`OidcCallbackServlet`).
- **Wariant 1 zablokowany przez realm (05.11.2026, po ponownej rejestracji z pustym `ESKARGA_PKCE`)**: realm `id-users` ma politykę
  klientów `idusers-klienci-przegladarkowi` (profil `idusers-rfc9700`, wykonawca `pkce-enforcer`, `auto-configure=true`) dla
  wszystkich klientów poufnych i publicznych (`id-users-keycloak-config/src/main/realm/id-users.json`). Keycloak dopisuje `S256`
  przy każdej aktualizacji klienta i wymusza PKCE na endpoincie autoryzacji niezależnie od atrybutu. Zdjęcie wymogu oznaczałoby
  zmianę polityki bezpieczeństwa całego ID-users (RFC 9700), nie konfiguracji eSkargi. Pozostaje wariant 2 (mechanizm z PKCE).

## Decyzja 05.11.2026 – wariant 2 zrealizowany: Elytron OIDC z PKCE S256

- `WEB-INF/oidc.json` (podsystem `elytron-oidc-client` WildFly 41): `enable-pkce=true`, klient poufny, `use-resource-role-mappings`
  (role `resource_access.eskarga.roles` → role kontenera), `principal-attribute=preferred_username`, `token-store=SESSION`,
  `logout-path=/wyloguj` z `post-logout-redirect-uri` na stronę „sesja zakończona”. Wartości `${env.ESKARGA_OIDC_*}` podstawia
  serwer (ten sam WAR na wszystkie środowiska – D11). Podsystem nie ma atrybutu PKCE w modelu `secure-deployment`, dlatego
  konfiguracja w WAR, nie w CLI. Metadane dostawcy są pobierane leniwie – tryb demo nie odpytuje Keycloaka, `LoginFilter`
  jest w nim nieaktywny.
- Odczyt tokenów: `ElytronTokens` (`OidcSecurityContext` w atrybucie żądania/sesji) zamiast `OpenIdContext`; `CurrentUser`
  i `MfaFilter` bez zależności od Jakarta Security (zostaje `SecurityContext` do `isCallerInRole`). `SecurityModeExtension`
  usunięte (brak adnotacji mechanizmu do wyłączania w demo).
- Elytron nie wysyła `acr_values` – poziom LoA 2 wymusza konfiguracja klienta `eskarga` w Keycloaku (`default.acr.values=mfa`,
  ustawiane przez skrypt rejestracji); `MfaFilter` nadal sprawdza `acr` w ID tokenie. Adresy zwrotne klienta: `<baza>/*`
  (Elytron używa bieżącego adresu strony jako `redirect_uri`). `/callback` zostaje jako strona błędu dostawcy.
- Wylogowanie: `LogoutBean` → `/wyloguj` (RP-initiated logout z `id_token_hint`), back-channel logout bez zmian (własny serwlet).
- `ESKARGA_PKCE` w rejestracji pozostaje przełącznikiem diagnostycznym; domyślnie `S256`.
- **Uzupełnienie 05.11.2026 – wyzwalacz logowania `/logowanie`**: programowe `HttpServletRequest.authenticate()` w `LoginFilter`
  kończyło się `UT010032: Authentication failed` bez przekierowania (mechanizm załadował metadane dostawcy, ale wyzwanie 302 nie
  dotarło do odpowiedzi). Elytron OIDC wysyła wyzwanie dla zasobu z ograniczeniem bezpieczeństwa, dlatego `web.xml` chroni
  jedyną ścieżkę `/logowanie` (`<role-name>**</role-name>`), `LoginFilter` zapamiętuje w sesji żądany adres (ścieżka w kontekście
  i zapytanie) i przekierowuje tam anonima, a `LoginTriggerServlet` po zalogowaniu wraca na zapamiętany adres (tylko ścieżka
  względna, domyślnie pulpit). `redirect_uri` to `<baza>/logowanie` – mieści się w `<baza>/*`. W dev CLI włącza logger
  `org.wildfly.security.http.oidc` na poziomie DEBUG.
- **Uzupełnienie 05.11.2026 – `private_key_jwt` klienta `eskarga`**: po przekierowaniu z ID-users wymiana kodu kończyła się
  `invalid_client: Parameter client_assertion_type is missing` – klient `eskarga` zarejestrowany narzędziem projektu id (T-037)
  ma `clientAuthenticatorType=client-jwt`, a Elytron wysyłał sekret. Elytron wybiera dostawcę poświadczeń po kluczu
  `credentials.provider`, więc `oidc.json` ma oba warianty (`secret`, `jwt` z PKCS#12), a `ESKARGA_OIDC_CLIENTAUTH` (`auto`/`jwt`/`secret`)
  wskazuje właściwy. W dev `entrypoint.sh` buduje PKCS#12 (openssl, hasło losowe na czas życia kontenera) z `eskarga.key.pem`
  zamontowanego z katalogu wyników narzędzia (`ID_USERS_KLUCZE_DIR`), ten sam klucz PEM służy klientowi dokumentów
  (`ESKARGA_DOCS_OIDC_PRIVATEKEYPEM`), a workery czytają `eskarga-workers.key.pem` (`ESKARGA_OIDC_PRIVATE_KEY_PEM`), gdy plik
  istnieje. Elytron nie wysyła `kid` w asercji – Keycloak dobiera klucz po algorytmie (RS256), co działa przy jednym aktywnym
  kluczu; `ClientCredentialsTokenClient` wysyła `kid` z pliku `<klient>.kid`. Produkcja: klucz w PKCS#12/HSM, do opisania w runbooku.

- **Uzupełnienie 05.11.2026 – podmiot i role z żądania**: po zalogowaniu strony renderowały się jako anonim bez ról (403
  „Brak uprawnień” bez paska użytkownika). `SecurityContext` Jakarta Security (Soteria) nie zwraca tożsamości mechanizmu Elytron
  OIDC, więc `CurrentUser` czyta podmiot z `HttpServletRequest.getUserPrincipal()` i role z `isUserInRole` (Elytron mapuje
  `resource_access.eskarga.roles` na role kontenera), z `SecurityContext` jako zapasem. `/wyloguj` (`logout-path`) dołączony do
  ograniczenia bezpieczeństwa – mechanizm obsługuje wylogowanie RP-initiated tylko na zasobie chronionym; `LogoutFallbackServlet`
  kończy sesję lokalną, gdy mechanizm nie przejął żądania (demo, brak sesji OIDC).
- **Uzupełnienie 05.11.2026 – adresy z rejestru ID-users**: po ponownej rejestracji narzędziem projektu id Keycloak odrzucił
  `redirect_uri=/eskarga/logowanie` („Nieprawidłowy parametr: redirect_uri”) – rejestr ID-users jest źródłem prawdy o kliencie
  i synchronizuje `redirectUris=[<baza>/callback]` oraz adres po wylogowaniu `<baza>/`. eSkarga trzyma się tych adresów:
  wyzwalaczem logowania i adresem zwrotnym jest `/callback` (`LoginTriggerServlet`, dawny `OidcCallbackServlet` scalony),
  `post-logout-redirect-uri` = `<baza>/`, a `LogoutBean` ustawia ciasteczko `ESKARGA_WYLOGOWANO`, po którym `LoginFilter`
  pokazuje stronę „sesja zakończona” zamiast od razu logować ponownie. Skrypt dev rejestruje te same adresy (plus `/*` i `/+`).
- **Uzupełnienie 05.11.2026 – `aud` asercji i własny dostawca `jwt-eskarga`**: z poprawnym kluczem Keycloak odpowiadał
  `invalid_client: Invalid token audience`. Wbudowany dostawca `jwt` Elytrona wpisuje w `aud` adres tokenu z metadanych
  (z kontenera `http://host.docker.internal:8180/…`), a Keycloak (`JWTClientValidator.getExpectedAudiences`) porównuje `aud`
  z adresem frontowym realmu (`http://localhost:8180/realms/id-users` i jego `…/protocol/openid-connect/token`), bo
  `KeycloakContext.getUri()` zwraca URI typu FRONTEND. W WAR-ze jest własny `ClientCredentialsProvider` (`jwt-eskarga`,
  `META-INF/services`): klucz PEM i `kid` z plików rejestracji ID-users, `aud` z `ESKARGA_OIDC_ASSERTIONAUDIENCE` (entrypoint:
  `<ESKARGA_OIDC_ISSUER>/protocol/openid-connect/token`). PKCS#12 i openssl w obrazie nie są już potrzebne.
  `ClientCredentialsTokenClient` (dokumenty, workery) ma `.assertionAudience` / `ESKARGA_OIDC_ASSERTION_AUDIENCE` z tego samego
  powodu. Narzędzie ID-users używa w `aud` issuera z discovery pobranego pod `localhost:8180` – stąd u niego działało.

- **Uzupełnienie 05.11.2026 – wylogowanie odnośnikiem GET**: przycisk „Wyloguj” jako `h:commandLink` kończył się
  `CSP nonce mismatch` – PrimeFaces 15 w trybie CSP dołącza `primefaces.nonce` tylko do własnych żądań (`core.js`), zwykły
  postback Mojarry go nie niesie. Wylogowanie to teraz odnośnik GET `/wylogowanie` (`LogoutServlet`): w oidc ciasteczko
  `ESKARGA_WYLOGOWANO` i przekierowanie na `/wyloguj` (logout-path Elytrona, zasób chroniony), w demo koniec sesji lokalnej.
  `LogoutBean` tylko przekierowuje tam z akcji JSF.
- **Uzupełnienie 05.11.2026 – CSP i nonce PrimeFaces**: `CSP nonce mismatch` na każdym żądaniu AJAX PrimeFaces (przełączanie
  Ts/Tw, zakładki, gęstość tabel) miało jedną przyczynę: `CspPhaseListener.initCsp` dokleja `" 'nonce-…';"` do **końca**
  `primefaces.CSP_POLICY`, a nasza polityka kończyła się `form-action 'self'` – nonce lądował w `form-action`, dyrektywy
  `script-src` nie było (skrypty spadały na `default-src 'self'`), przeglądarka blokowała `PrimeFaces.csp.init(nonce)`
  i `core.js` wysyłał żądania bez `primefaces.nonce`. Polityka kończy się teraz `script-src 'self'` (web.xml). Wylogowanie
  odnośnikiem GET zostaje (niezależne od nonce).
