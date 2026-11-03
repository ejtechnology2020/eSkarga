package pl.ejtechnology.tk.eSkarga.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;

import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.Person;

/**
 * Bieżący użytkownik (D4): tożsamość i role ogólne z ID-users, przydziały i upoważnienia z aplikacji.
 * W trybie demo tożsamość pochodzi z danych przykładowych, a rolę wybiera się parametrem {@code ?rola=}.
 */
@Named("currentUser")
@SessionScoped
public class CurrentUser implements Serializable {

    public static final String DEMO_ROLE_ATTR = "eskarga.demo.role";

    @Inject
    private SecurityContext securityContext;
    @Inject
    private OidcSettings settings;
    @Inject
    private DemoDataset demo;
    @Inject
    private Fmt fmt;
    @Inject
    private HttpServletRequest request;

    private AppRole demoRole;

    /** Rola do podglądu wariantów ekranów (tylko tryb demo). */
    public void switchDemoRole(String code) {
        if (settings.isDemo()) {
            demoRole = AppRole.fromShortCode(code);
        }
    }

    private Optional<Person> demoPerson() {
        if (!settings.isDemo()) {
            return Optional.empty();
        }
        Object attr = request.getSession(false) == null ? null : request.getSession(false).getAttribute(DEMO_ROLE_ATTR);
        AppRole role = attr instanceof AppRole r ? r : demoRole;
        if (role == null) {
            role = AppRole.fromShortCode(OidcSettings.value("eskarga.demo.role", "ref"));
        }
        demoRole = role;
        return Optional.of(demo.personFor(role));
    }

    public boolean isAuthenticated() {
        return getPrincipal() != null || demoPerson().isPresent();
    }

    public Principal getPrincipal() {
        return securityContext == null ? null : securityContext.getCallerPrincipal();
    }

    public String getLogin() {
        Principal p = getPrincipal();
        return p != null ? p.getName() : demoPerson().map(Person::login).orElse("anonim");
    }

    public String getDisplayName() {
        return demoPerson().map(Person::displayName).orElseGet(this::getLogin);
    }

    public String getInitials() {
        return demoPerson().map(Person::initials).orElseGet(() -> initials(getDisplayName()));
    }

    /** Opis roli pod nazwiskiem w górnym pasku, np. „referent Wydziału Weryfikacyjnego”. */
    public String getRoleLabel() {
        return demoPerson().map(Person::roleLabel).orElseGet(() -> fmt.msg("role." + getRole().name()));
    }

    /** Rola wiodąca do budowy menu i wariantów ekranów. */
    public AppRole getRole() {
        Optional<Person> p = demoPerson();
        if (p.isPresent()) {
            return p.get().role();
        }
        for (AppRole r : AppRole.values()) {
            if (securityContext.isCallerInRole(r.name())) {
                return r;
            }
        }
        return AppRole.TK_REFERENT;
    }

    public Set<AppRole> getRoles() {
        Optional<Person> p = demoPerson();
        if (p.isPresent()) {
            return p.get().multiRole() ? EnumSet.of(p.get().role(), AppRole.TK_SEDZIA) : EnumSet.of(p.get().role());
        }
        EnumSet<AppRole> roles = EnumSet.noneOf(AppRole.class);
        for (AppRole r : AppRole.values()) {
            if (securityContext.isCallerInRole(r.name())) {
                roles.add(r);
            }
        }
        return roles;
    }

    /** Rola wybrana w górnym pasku (użytkownik z kilkoma rolami); w trybie demo przełącza wariant ekranu. */
    public AppRole getSelectedRole() {
        return getRole();
    }

    public void setSelectedRole(AppRole role) {
        if (role != null && settings.isDemo()) {
            demoRole = role;
            if (request.getSession(false) != null) {
                request.getSession(false).setAttribute(DEMO_ROLE_ATTR, role);
            }
        }
    }

    public boolean hasRole(String role) {
        return getRoles().contains(AppRole.fromShortCode(role));
    }

    public boolean isMultiRole() {
        return getRoles().size() > 1;
    }

    public int getNotifications() {
        return demoPerson().map(Person::notifications).orElse(0);
    }

    public boolean isDemo() {
        return settings.isDemo();
    }

    /** Czy użytkownik działa jako Prezes TK na podstawie upoważnienia (BR-10). */
    public boolean isActingAsPresident() {
        return getRole() == AppRole.TK_PREZES;
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.replace("sędzia TK ", "").split("\\s+")) {
            if (!part.isEmpty() && Character.isLetter(part.charAt(0)) && !part.endsWith(".")) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.length() > 2 ? sb.substring(sb.length() - 2) : sb.toString();
    }
}
