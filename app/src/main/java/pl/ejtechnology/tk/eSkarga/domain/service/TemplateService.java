package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.List;
import java.util.Optional;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.model.TemplateStatus;

/**
 * Szablony pism (T-026a, FR-35, NFR-15, D13): wersje w bazie, walidacja przed aktywacją przez usługę dokumentów
 * (kompilacja, render na danych testowych, veraPDF), aktywacja z akceptacją drugiej osoby, historia w dzienniku zdarzeń.
 * Pisma już wygenerowane pamiętają identyfikator wersji szablonu, więc nowa aktywacja ich nie zmienia.
 */
@Stateless
public class TemplateService {

    /** Wynik walidacji szablonu (z usługi dokumentów). */
    public record Validation(boolean ok, List<String> errors) {
    }

    /** Walidator – implementacja REST do docs-service (klient) albo testowa. */
    public interface TemplateValidator {
        Validation validate(String code, String sourceXhtml);
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    TemplateValidator validator;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LetterTemplate saveDraft(String code, String name, String source, String authorId) {
        int next = em.createQuery("select coalesce(max(t.version), 0) from LetterTemplate t where t.code = :c", Integer.class).setParameter("c", code).getSingleResult() + 1;
        LetterTemplate t = new LetterTemplate(code, name, next, source, authorId);
        em.persist(t);
        audit.record(authorId, "TK_ADMIN", "zapis projektu szablonu", code + " v" + next, null, null);
        return t;
    }

    /** Aktywacja: walidacja (błąd składni blokuje), akceptacja drugiej osoby, wycofanie poprzedniej aktywnej wersji. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Validation activate(long templateId, String approverId) {
        LetterTemplate t = em.find(LetterTemplate.class, templateId);
        Validation v = validator.validate(t.getCode(), t.getSourceXhtml());
        if (!v.ok()) {
            audit.record(approverId, "TK_ADMIN", "aktywacja szablonu odrzucona przez walidację", t.getCode() + " v" + t.getVersion(), null, null);
            return v;
        }
        active(t.getCode()).ifPresent(LetterTemplate::withdraw);
        t.activate(approverId);
        audit.record(approverId, "TK_ADMIN", "aktywacja szablonu", t.getCode() + " v" + t.getVersion() + " (autor " + t.getAuthorUserId() + ")", null, null);
        return v;
    }

    /** Zasiewa tabelę szablonami domyślnymi z usługi dokumentów (wersja 1, aktywna) tylko dla kodów bez żadnej wersji. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public int seedDefaults(java.util.Map<String, String> defaults, String actorId) {
        int added = 0;
        for (var e : defaults.entrySet()) {
            Long count = em.createQuery("select count(t) from LetterTemplate t where t.code = :c", Long.class).setParameter("c", e.getKey()).getSingleResult();
            if (count == 0) {
                LetterTemplate t = new LetterTemplate(e.getKey(), e.getKey(), 1, e.getValue(), "system");
                t.activate(actorId);
                em.persist(t);
                added++;
            }
        }
        if (added > 0) {
            audit.record(actorId, "TK_ADMIN", "zasianie szablonów domyślnych: " + added, null, null, null);
        }
        return added;
    }

    public Optional<LetterTemplate> active(String code) {
        return em.createQuery("select t from LetterTemplate t where t.code = :c and t.status = :s", LetterTemplate.class)
                .setParameter("c", code).setParameter("s", TemplateStatus.AKTYWNA).getResultStream().findFirst();
    }

    public List<LetterTemplate> all() {
        return em.createQuery("select t from LetterTemplate t order by t.code, t.version desc", LetterTemplate.class).getResultList();
    }
}
