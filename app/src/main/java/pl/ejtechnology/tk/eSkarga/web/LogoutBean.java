package pl.ejtechnology.tk.eSkarga.web;

import java.io.IOException;
import java.io.Serializable;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;

import pl.ejtechnology.tk.eSkarga.security.OidcSettings;

/** Wylogowanie lokalne (unieważnienie sesji) i przekierowanie do ID-users (logout OIDC, T-004). */
@Named("logoutBean")
@RequestScoped
public class LogoutBean implements Serializable {

    @Inject
    private OidcSettings settings;

    public void logout() throws IOException {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        HttpServletRequest req = (HttpServletRequest) ec.getRequest();
        if (!settings.isDemo()) {
            // Elytron OIDC: logout-path z oidc.json – unieważnia sesję, kończy sesję SSO w ID-users (end_session, id_token_hint)
            // i wraca na post-logout-redirect-uri (strona „sesja zakończona”)
            ec.redirect(ec.getRequestContextPath() + "/wyloguj");
            fc.responseComplete();
            return;
        }
        try {
            req.logout();
        } catch (Exception ignored) {
            // brak mechanizmu uwierzytelniania w trybie demo
        }
        ec.invalidateSession();
        ec.redirect(ec.getRequestContextPath() + "/blad/sesja.xhtml");
        fc.responseComplete();
    }
}
