package pl.ejtechnology.tk.eSkarga.web;

import java.io.IOException;
import java.io.Serializable;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.LogoutServlet;

/** Wylogowanie lokalne (unieważnienie sesji) i przekierowanie do ID-users (logout OIDC, T-004). */
@Named("logoutBean")
@RequestScoped
public class LogoutBean implements Serializable {

    /** Całą logikę ma {@code LogoutServlet} (GET {@code /wylogowanie}); bean zostaje dla wywołań z akcji JSF. */
    public void logout() throws IOException {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.redirect(ec.getRequestContextPath() + LogoutServlet.PATH);
        fc.responseComplete();
    }
}
