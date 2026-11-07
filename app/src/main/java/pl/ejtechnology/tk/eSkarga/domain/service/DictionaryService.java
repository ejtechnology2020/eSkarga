package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.domain.model.Authority;
import pl.ejtechnology.tk.eSkarga.domain.model.Court;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterType;
import pl.ejtechnology.tk.eSkarga.domain.model.ReviewStandard;

/** Słowniki (FR-35, UC-24, S-24): sądy, organy, typy pism, wzorce kontroli. Pozycje wycofuje się, nie usuwa. */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class DictionaryService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;

    public List<Court> courts() { return em.createQuery("select c from Court c order by c.name", Court.class).getResultList(); }
    public List<Authority> authorities() { return em.createQuery("select a from Authority a order by a.name", Authority.class).getResultList(); }
    public List<LetterType> letterTypes() { return em.createQuery("select t from LetterType t order by t.name", LetterType.class).getResultList(); }
    public List<ReviewStandard> reviewStandards() { return em.createQuery("select s from ReviewStandard s order by s.code", ReviewStandard.class).getResultList(); }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public <T> T add(T entry, String adminId) {
        em.persist(entry);
        audit.record(adminId, "TK_ADMIN", "dodanie pozycji słownika", entry.getClass().getSimpleName(), null, null);
        return entry;
    }

    public List<pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory> archiveCategories() {
        return em.createQuery("select a from ArchiveCategory a order by a.closingMode", pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory.class).getResultList();
    }

    /** Kategoria archiwalna i okres przechowywania (lata, {@code null} = wieczyście) – konfiguracja, nie kod (ADR 0012). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory updateArchiveCategory(pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode mode, String category, Integer retentionYears, String adminId) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Kategoria archiwalna jest wymagana");
        }
        if (retentionYears != null && retentionYears <= 0) {
            throw new IllegalArgumentException("Okres przechowywania musi być dodatni albo pusty (wieczyście)");
        }
        var current = em.find(pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory.class, mode);
        if (current == null) {
            current = new pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory(mode, category, retentionYears);
            em.persist(current);
        } else {
            current.update(category, retentionYears, adminId, java.time.Instant.now());
        }
        audit.record(adminId, "TK_ADMIN", "zmiana kategorii archiwalnej " + mode + " → " + category + " / " + (retentionYears == null ? "wieczyście" : retentionYears + " lat"), null, null, null);
        return current;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public void withdrawReviewStandard(long id, String adminId) {
        ReviewStandard s = em.find(ReviewStandard.class, id);
        s.withdraw();
        audit.record(adminId, "TK_ADMIN", "wycofanie wzorca kontroli", s.getCode(), null, null);
    }
}
