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

    /** Poprawka projektu w miejscu (S-24 „Edytuj”): tylko PROJEKT; inne stany – nowa wersja przez {@link #saveDraft}. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LetterTemplate updateDraft(long templateId, String name, String source, String actorId) {
        LetterTemplate t = require(templateId);
        t.updateDraft(name, source);
        audit.record(actorId, "TK_ADMIN", "zmiana projektu szablonu", t.getCode() + " v" + t.getVersion(), null, null);
        return t;
    }

    /** Wycofanie aktywnej wersji bez następcy – pisma z tego kodu nie powstaną do aktywacji innej wersji. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LetterTemplate withdraw(long templateId, String actorId) {
        LetterTemplate t = require(templateId);
        if (t.getStatus() != TemplateStatus.AKTYWNA) {
            throw new IllegalStateException("Wycofać można tylko aktywną wersję");
        }
        t.withdraw();
        audit.record(actorId, "TK_ADMIN", "wycofanie szablonu", t.getCode() + " v" + t.getVersion(), null, null);
        return t;
    }

    /** Usunięcie logiczne (status USUNIETA): projekt albo wersja wycofana; aktywnej nie (D13, rola bez DELETE). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public LetterTemplate delete(long templateId, String actorId) {
        LetterTemplate t = require(templateId);
        if (t.getStatus() == TemplateStatus.USUNIETA) {
            return t;
        }
        t.markDeleted();
        audit.record(actorId, "TK_ADMIN", "usunięcie szablonu", t.getCode() + " v" + t.getVersion() + " (pisma: " + usageCount(templateId) + ")", null, null);
        return t;
    }

    public LetterTemplate require(long templateId) {
        LetterTemplate t = em.find(LetterTemplate.class, templateId);
        if (t == null) {
            throw new IllegalArgumentException("Brak szablonu " + templateId);
        }
        return t;
    }

    public Optional<LetterTemplate> find(long templateId) {
        return Optional.ofNullable(em.find(LetterTemplate.class, templateId));
    }

    /** Liczba pism wygenerowanych z danej wersji (case_document.template_version_id). */
    public long usageCount(long templateId) {
        return em.createQuery("select count(d) from CaseDocument d where d.templateVersion.id = :id", Long.class).setParameter("id", templateId).getSingleResult();
    }

    /** Liczba pism na wersję szablonu – do listy S-24. */
    public java.util.Map<Long, Long> usageCounts() {
        java.util.Map<Long, Long> out = new java.util.HashMap<>();
        for (Object[] row : em.createQuery("select d.templateVersion.id, count(d) from CaseDocument d where d.templateVersion is not null group by d.templateVersion.id", Object[].class).getResultList()) {
            out.put((Long) row[0], (Long) row[1]);
        }
        return out;
    }

    public Optional<LetterTemplate> active(String code) {
        return em.createQuery("select t from LetterTemplate t where t.code = :c and t.status = :s", LetterTemplate.class)
                .setParameter("c", code).setParameter("s", TemplateStatus.AKTYWNA).getResultStream().findFirst();
    }

    /** Wersje bez usuniętych logicznie; kolejność: kod, wersja malejąco. */
    public List<LetterTemplate> all() {
        return em.createQuery("select t from LetterTemplate t where t.status <> :deleted order by t.code, t.version desc", LetterTemplate.class)
                .setParameter("deleted", TemplateStatus.USUNIETA).getResultList();
    }
}
