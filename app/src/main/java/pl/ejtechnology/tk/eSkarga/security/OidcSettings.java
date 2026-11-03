package pl.ejtechnology.tk.eSkarga.security;

import java.io.Serializable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Konfiguracja OIDC dla ID-users (T-004, D3). Kontrakt ID-users nie jest jeszcze znany, dlatego issuer,
 * identyfikatory klienta, nazwy claimów ról i grup oraz claim MFA są konfigurowalne przez system properties
 * WildFly albo zmienne środowiskowe. Niczego nie zgadujemy na sztywno.
 */
@Named("oidcSettings")
@ApplicationScoped
public class OidcSettings implements Serializable {

    public static final String MODE_OIDC = "oidc";
    /** Tryb bez dostawcy tożsamości – tylko do pracy nad interfejsem na stacji deweloperskiej. */
    public static final String MODE_DEMO = "demo";

    public String getMode() {
        return value("eskarga.security.mode", MODE_DEMO);
    }

    public boolean isDemo() {
        return MODE_DEMO.equalsIgnoreCase(getMode());
    }

    public String getIssuer() {
        return value("eskarga.oidc.issuer", "https://id-users.invalid/realms/tk");
    }

    public String getClientId() {
        return value("eskarga.oidc.clientId", "eskarga");
    }

    public String getClientSecret() {
        return value("eskarga.oidc.clientSecret", "");
    }

    /** Nazwa claimu z rolami (np. {@code roles} albo {@code realm_access.roles}) – do potwierdzenia z zespołem ID-users. */
    public String getRolesClaim() {
        return value("eskarga.oidc.rolesClaim", "roles");
    }

    public String getGroupsClaim() {
        return value("eskarga.oidc.groupsClaim", "groups");
    }

    public String getNameClaim() {
        return value("eskarga.oidc.nameClaim", "preferred_username");
    }

    /** Claim poziomu uwierzytelnienia ({@code acr} albo {@code amr}). */
    public String getMfaClaim() {
        return value("eskarga.oidc.mfaClaim", "acr");
    }

    /** Wymagana wartość claimu MFA; pusta = nie wymuszaj (tylko dev). */
    public String getMfaRequiredValue() {
        return value("eskarga.oidc.mfaRequiredValue", "mfa");
    }

    /** Prefiks ról ID-users mapowanych 1:1 na role aplikacji, np. {@code TK_}. */
    public String getRolePrefix() {
        return value("eskarga.oidc.rolePrefix", "");
    }

    /** Odczyt ustawienia z system property albo zmiennej środowiskowej (ESKARGA_OIDC_ISSUER itd.). */
    public static String value(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return v == null || v.isBlank() ? def : v;
    }
}
