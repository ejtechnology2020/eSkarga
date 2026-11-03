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

    /** Issuer ID-users (kontrakt 03.11.2026): test {@code https://login.test.id-users.example.com/realms/id-users}, dev {@code http://localhost:8180/realms/id-users}. */
    public String getIssuer() {
        return value("eskarga.oidc.issuer", "http://localhost:8180/realms/id-users");
    }

    public String getClientId() {
        return value("eskarga.oidc.clientId", "eskarga");
    }

    public String getClientSecret() {
        return value("eskarga.oidc.clientSecret", "");
    }

    /**
     * Ścieżka claimu z rolami aplikacji. Kontrakt ID-users: role klienta są pod {@code resource_access.<client_id>.roles}
     * (wymaga zakresu {@code roles} przy rejestracji); role realmu – {@code realm_access.roles}.
     */
    public String getRolesClaim() {
        return value("eskarga.oidc.rolesClaim", "resource_access." + getClientId() + ".roles");
    }

    /** Dodatkowa ścieżka ról (realm) – role globalne ID-users mapowane tak samo; pusta = pomijana. */
    public String getRealmRolesClaim() {
        return value("eskarga.oidc.realmRolesClaim", "realm_access.roles");
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

    /** Wymagane wartości {@code acr} (ID-users: {@code haslo} = LoA 1, {@code mfa} = LoA 2, {@code wysoki} = LoA 3); pusta = nie wymuszaj (tylko dev). */
    public String getMfaRequiredValue() {
        return value("eskarga.oidc.mfaRequiredValue", "mfa,wysoki");
    }

    /** Parametry żądania autoryzacji: {@code acr_values} z najniższym wymaganym poziomem (ID-users honoruje {@code acr_values=mfa}). */
    public String[] getExtraParameters() {
        String required = getMfaRequiredValue();
        if (required.isBlank()) {
            return new String[0];
        }
        return new String[] {"acr_values=" + required.split(",")[0].trim()};
    }

    /** Oczekiwany odbiorca tokenów ({@code aud}/{@code azp}) – domyślnie identyfikator klienta. */
    public String getAudience() {
        return value("eskarga.oidc.audience", getClientId());
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
