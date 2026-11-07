package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.JudgeFunction;
import pl.ejtechnology.tk.eSkarga.domain.model.Judge;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Słownik sędziów (S-24, ADR 0027): jedyne źródło sędziów do wyznaczenia (S-17, UC-05) i składów (S-18, UC-11). Wyznaczyć można
 * tylko sędziego z aktywnym wpisem i w kadencji w dniu czynności. Zmiany tylko przez administratora, każda w dzienniku zdarzeń.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class JudgeDictionaryService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    /** Wszyscy (także wycofani i poza kadencją) – do zarządzania na S-24; Prezes i Wiceprezes pierwsi. */
    public List<Judge> all() {
        return em.createQuery("select j from Judge j order by j.active desc, j.function, j.displayName", Judge.class).getResultList();
    }

    /** Sędziowie, których można wyznaczyć w dniu {@code day}. */
    public List<Judge> eligibleOn(LocalDate day) {
        return all().stream().filter(j -> j.eligibleOn(day)).toList();
    }

    public Optional<Judge> byUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return em.createQuery("select j from Judge j where j.userId = :u", Judge.class).setParameter("u", userId).getResultStream().findFirst();
    }

    public Optional<Judge> find(long id) {
        return Optional.ofNullable(em.find(Judge.class, id));
    }

    /** Nazwa do pism („sędzia TK …”) albo login, gdy nie ma wpisu. */
    public String displayName(String userId) {
        return byUserId(userId).map(Judge::titledName).orElse(userId);
    }

    /** Sędzia ze słownika, aktywny i w kadencji w dniu czynności – inaczej wyjątek z czytelnym powodem. */
    public Judge requireEligible(String userId, LocalDate day) {
        Judge j = byUserId(userId).orElseThrow(() -> new IllegalArgumentException("Sędzia " + userId + " nie jest w słowniku sędziów (Administracja → Sędziowie)"));
        if (!j.isActive()) {
            throw new IllegalArgumentException("Wpis sędziego " + j.getDisplayName() + " jest wycofany");
        }
        if (!j.eligibleOn(day)) {
            throw new IllegalArgumentException("Sędzia " + j.getDisplayName() + " jest poza kadencją w dniu " + day + " (" + j.getTermFrom() + " – " + (j.getTermTo() == null ? "…" : j.getTermTo()) + ")");
        }
        return j;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public Judge add(String userId, String displayName, JudgeFunction function, LocalDate termFrom, LocalDate termTo, String adminId) {
        String login = userId == null ? "" : userId.trim();
        if (login.isEmpty() || !login.matches("[A-Za-z0-9._@-]{1,100}")) {
            throw new IllegalArgumentException("Login sędziego z ID-users jest wymagany (litery, cyfry, kropka, myślnik)");
        }
        validate(displayName, function, termFrom, termTo);
        if (byUserId(login).isPresent()) {
            throw new IllegalArgumentException("Sędzia o loginie " + login + " jest już w słowniku");
        }
        Judge j = new Judge(login, displayName.trim(), function, termFrom, termTo, adminId, clock.instant());
        em.persist(j);
        audit.record(adminId, "TK_ADMIN", "dodanie sędziego do słownika: " + login + " (" + function.label() + ", kadencja " + termFrom + " – " + (termTo == null ? "…" : termTo) + ")", null, null, null);
        return j;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public Judge update(long id, String displayName, JudgeFunction function, LocalDate termFrom, LocalDate termTo, String adminId) {
        validate(displayName, function, termFrom, termTo);
        Judge j = require(id);
        j.update(displayName.trim(), function, termFrom, termTo, adminId, clock.instant());
        audit.record(adminId, "TK_ADMIN", "zmiana wpisu sędziego: " + j.getUserId() + " (" + function.label() + ", kadencja " + termFrom + " – " + (termTo == null ? "…" : termTo) + ")", null, null, null);
        return j;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public Judge setActive(long id, boolean active, String adminId) {
        Judge j = require(id);
        if (j.isActive() != active) {
            j.setActive(active, adminId, clock.instant());
            audit.record(adminId, "TK_ADMIN", (active ? "przywrócenie" : "wycofanie") + " sędziego w słowniku: " + j.getUserId(), null, null, null);
        }
        return j;
    }

    public Judge require(long id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Brak sędziego " + id + " w słowniku"));
    }

    private static void validate(String displayName, JudgeFunction function, LocalDate termFrom, LocalDate termTo) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Imię i nazwisko sędziego są wymagane");
        }
        if (function == null) {
            throw new IllegalArgumentException("Funkcja jest wymagana");
        }
        if (termFrom == null) {
            throw new IllegalArgumentException("Początek kadencji jest wymagany");
        }
        if (termTo != null && termTo.isBefore(termFrom)) {
            throw new IllegalArgumentException("Koniec kadencji nie może być wcześniejszy niż początek");
        }
    }
}
