package pl.ejtechnology.tk.eSkarga.security;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;

/**
 * Rozszerzenie CDI: w trybie {@code demo} wyłącza definicję mechanizmu OIDC, żeby aplikacja uruchomiła się
 * bez dostawcy tożsamości (praca nad interfejsem). Na środowiskach test/prod tryb musi być {@code oidc}.
 */
public class SecurityModeExtension implements Extension {

    void vetoOidcInDemoMode(@Observes ProcessAnnotatedType<OidcSecurityConfig> pat) {
        if (new OidcSettings().isDemo()) {
            pat.veto();
        }
    }
}
