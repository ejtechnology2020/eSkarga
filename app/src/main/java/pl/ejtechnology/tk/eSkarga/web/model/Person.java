package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.security.AppRole;

/** Użytkownik z danych przykładowych (brief §9.1). */
public record Person(String login, String displayName, String roleLabel, String initials, AppRole role, boolean multiRole,
                     int notifications) implements Serializable {
}
