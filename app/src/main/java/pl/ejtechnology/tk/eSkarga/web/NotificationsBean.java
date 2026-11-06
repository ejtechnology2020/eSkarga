package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.LoginFilter;
import pl.ejtechnology.tk.eSkarga.web.model.NotificationItem;

/**
 * Dzwonek na górnym pasku: lista z {@link ViewData#notifications} liczona dla zalogowanej osoby, licznik = pozycje jeszcze
 * nieobejrzane w tej sesji (klucze obejrzanych w sesji; po otwarciu panelu licznik gaśnie, nowe pozycje znów go podnoszą).
 * „Nowe od poprzedniego logowania” – czas z rejestru S-22 ({@link LoginFilter#PREVIOUS_LOGIN_ATTRIBUTE}); przy pierwszym
 * logowaniu ostatnie 7 dni. Bez tabeli powiadomień w bazie.
 */
@Named("powiadomienia")
@SessionScoped
public class NotificationsBean implements Serializable {

    static final long FIRST_LOGIN_WINDOW_DAYS = 7;

    @Inject
    private ViewData data;
    @Inject
    private AppClock clock;

    private final Set<String> seen = new HashSet<>();
    private boolean open;
    private Instant since;

    public List<NotificationItem> getItems() {
        return data.notifications(since());
    }

    /** Liczba pozycji nieobejrzanych w tej sesji – 0 ukrywa kółko. */
    public int getCount() {
        int n = 0;
        for (NotificationItem i : getItems()) {
            if (!seen.contains(i.klucz())) {
                n++;
            }
        }
        return n;
    }

    public boolean isOpen() { return open; }

    /** Kliknięcie dzwonka: otwarcie oznacza wszystkie bieżące pozycje jako obejrzane; drugie kliknięcie zamyka. */
    public void toggle() {
        open = !open;
        if (open) {
            markSeen();
        }
    }

    public void close() { open = false; }

    void markSeen() {
        for (NotificationItem i : getItems()) {
            seen.add(i.klucz());
        }
    }

    Instant since() {
        if (since == null) {
            Instant previous = null;
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                Object v = fc.getExternalContext().getSessionMap().get(LoginFilter.PREVIOUS_LOGIN_ATTRIBUTE);
                if (v instanceof Instant p) {
                    previous = p;
                }
            }
            since = previous != null ? previous : clock.instant().minusSeconds(FIRST_LOGIN_WINDOW_DAYS * 86_400);
        }
        return since;
    }

    void setSince(Instant since) { this.since = since; }
}
