package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Osoba znana eSkardze (S-22): konta i role prowadzi ID-users (D4), a aplikacja zapamiętuje przy logowaniu login, nazwę
 * wyświetlaną i role z tokenu oraz czas ostatniego logowania – to jedyne źródło listy użytkowników po stronie eSkargi
 * (ID-users nie udostępnia API posiadaczy ról aplikacji). Dane: login i nazwa (minimum potrzebne administratorowi, NFR-02).
 */
@Entity
@Table(name = "app_user_seen")
public class KnownUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, length = 100, unique = true)
    private String login;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Role eSkargi z ostatniego tokenu, rozdzielone przecinkiem (np. {@code TK_REFERENT,TK_KIEROWNIK}). */
    @Column(name = "roles", nullable = false, length = 400)
    private String roles;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    @Column(name = "login_count", nullable = false)
    private int loginCount;

    protected KnownUser() {
    }

    public KnownUser(String login, String displayName, String roles, Instant now) {
        this.login = login;
        this.displayName = displayName;
        this.roles = roles;
        this.firstSeenAt = now;
        this.lastLoginAt = now;
        this.loginCount = 1;
    }

    public void seen(String displayName, String roles, Instant now) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        this.roles = roles;
        this.lastLoginAt = now;
        this.loginCount++;
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getDisplayName() { return displayName; }
    public String getRoles() { return roles; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getLoginCount() { return loginCount; }
}
