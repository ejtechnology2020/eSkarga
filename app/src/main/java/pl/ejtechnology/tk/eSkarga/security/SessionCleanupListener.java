package pl.ejtechnology.tk.eSkarga.security;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/** Usuwa sesję z {@link SessionRegistry} po wygaśnięciu albo wylogowaniu. */
@WebListener
public class SessionCleanupListener implements HttpSessionListener {

    @Inject
    SessionRegistry registry;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (registry != null) {
            registry.unregister(se.getSession());
        }
    }
}
