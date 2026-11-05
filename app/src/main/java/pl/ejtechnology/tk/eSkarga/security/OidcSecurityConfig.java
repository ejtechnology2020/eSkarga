package pl.ejtechnology.tk.eSkarga.security;

import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Logowanie przez ID-users jako dostawcę OIDC (Authorization Code + PKCE S256, klient poufny; D3, T-004).
 * Tokeny zostają na serwerze (sesja HTTP); przeglądarka dostaje tylko ciasteczko sesji.
 *
 * <p>Mechanizm: Elytron OIDC ({@code WEB-INF/oidc.json}, podsystem {@code elytron-oidc-client} WildFly 41) – ADR 0002,
 * wariant 2 (05.11.2026). Jakarta Security ({@code @OpenIdAuthenticationMechanismDefinition}, Soteria) odrzucone, bo nie
 * wysyła PKCE, którego wymaga polityka klientów realmu ID-users (RFC 9700). Wartości konfiguracji to zmienne środowiskowe
 * {@code ESKARGA_OIDC_*} podstawiane przez serwer; role {@code resource_access.<client>.roles} stają się rolami kontenera
 * ({@code use-resource-role-mappings}), a tokeny czyta {@link ElytronTokens}. Logowanie wymusza {@link LoginFilter}
 * (w trybie demo nieaktywny; metadane dostawcy są pobierane leniwie, więc bez IdP nic nie jest odpytywane).
 *
 * <p>Uwaga do {@code oidc.json}: parser Elytrona odrzuca nieznane pola (także komentarze w stylu {@code _komentarz}) –
 * plik zawiera wyłącznie klucze z {@code OidcJsonConfiguration}; wartości {@code ${env.X:domyślna}} podstawia serwer.
 */
@DeclareRoles({"TK_REFERENT", "TK_ASYSTENT", "TK_SEDZIA", "TK_PREZES", "TK_KIEROWNIK", "TK_BIURO_PODAWCZE",
        "TK_ARCHIWISTA", "TK_ADMIN"})
@ApplicationScoped
public class OidcSecurityConfig {
}
