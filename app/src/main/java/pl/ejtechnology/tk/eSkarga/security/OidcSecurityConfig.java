package pl.ejtechnology.tk.eSkarga.security;

import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.authentication.mechanism.http.OpenIdAuthenticationMechanismDefinition;
import jakarta.security.enterprise.authentication.mechanism.http.openid.ClaimsDefinition;
import jakarta.security.enterprise.authentication.mechanism.http.openid.LogoutDefinition;
import jakarta.security.enterprise.authentication.mechanism.http.openid.PromptType;

/**
 * Logowanie przez ID-users jako dostawcę OIDC (Authorization Code + PKCE, klient poufny; D3, T-004).
 * Tokeny zostają na serwerze (sesja HTTP); przeglądarka dostaje tylko ciasteczko sesji.
 *
 * <p>Wybrano mechanizm Jakarta Security ({@code @OpenIdAuthenticationMechanismDefinition}) zamiast
 * {@code elytron-oidc-client}, bo konfiguracja jest w kodzie z EL i jednym WAR-em na wszystkie środowiska (D11);
 * uzasadnienie w ADR 0002. W trybie {@code eskarga.security.mode=demo} typ jest wyłączany przez
 * {@link SecurityModeExtension}, żeby serwer nie próbował odpytać nieistniejącego dostawcy.
 */
@OpenIdAuthenticationMechanismDefinition(
        providerURI = "#{oidcSettings.providerUri}",
        clientId = "#{oidcSettings.clientId}",
        clientSecret = "#{oidcSettings.clientSecret}",
        redirectURI = "${baseURL}/callback",
        scope = {"openid", "profile", "email"},
        prompt = PromptType.NONE,
        useNonce = true,
        useSession = true,
        redirectToOriginalResource = true,
        extraParametersExpression = "#{oidcSettings.extraParameters}",
        claimsDefinition = @ClaimsDefinition(
                callerNameClaim = "#{oidcSettings.nameClaim}",
                callerGroupsClaim = "#{oidcSettings.rolesClaim}"),
        // role aplikacji są dodatkowo odczytywane z tokenu dostępu przez RoleMapper (ścieżka resource_access.<client>.roles)
        logout = @LogoutDefinition(notifyProvider = true, redirectURI = "${baseURL}/"))
@DeclareRoles({"TK_REFERENT", "TK_ASYSTENT", "TK_SEDZIA", "TK_PREZES", "TK_KIEROWNIK", "TK_BIURO_PODAWCZE",
        "TK_ARCHIWISTA", "TK_ADMIN"})
@ApplicationScoped
public class OidcSecurityConfig {
}
