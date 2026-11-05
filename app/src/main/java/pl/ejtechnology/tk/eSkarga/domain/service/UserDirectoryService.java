package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.domain.model.KnownUser;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/** Rejestr osób znanych eSkardze (S-22): aktualizowany przy każdym logowaniu przez ID-users ({@code LoginFilter}). */
@Stateless
public class UserDirectoryService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AppClock clock;

    /** Zapis lub aktualizacja wpisu po loginie; role zapisywane w stałej kolejności {@link AppRole}. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public KnownUser touch(String login, String displayName, Set<AppRole> roles) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login jest wymagany");
        }
        String roleText = roles == null ? "" : roles.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        String name = displayName == null ? null : (displayName.length() > 200 ? displayName.substring(0, 200) : displayName);
        List<KnownUser> found = em.createQuery("select u from KnownUser u where u.login = :l", KnownUser.class).setParameter("l", login).getResultList();
        if (found.isEmpty()) {
            KnownUser u = new KnownUser(login, name, roleText, clock.instant());
            em.persist(u);
            return u;
        }
        KnownUser u = found.get(0);
        u.seen(name, roleText, clock.instant());
        return u;
    }

    public List<KnownUser> all() {
        return em.createQuery("select u from KnownUser u order by lower(coalesce(u.displayName, u.login))", KnownUser.class).getResultList();
    }
}
