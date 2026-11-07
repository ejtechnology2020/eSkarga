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
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.RequirementCondition;
import pl.ejtechnology.tk.eSkarga.domain.model.FormalRequirement;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Słownik wymogów formalnych listy kontrolnej (S-24, UC-06, FR-11, ADR 0015): osobno Ts i Tw, zarządzany przez administratora.
 * Kod wymogu jest stały (oceny w {@code checklist_entry} odwołują się do kodu); pozycje wycofuje się, nie usuwa. Każda zmiana w dzienniku.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class RequirementCatalogService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    /** Aktywne wymogi w kolejności listy. */
    public List<FormalRequirement> active(DocketSymbol symbol) {
        return em.createQuery("select r from FormalRequirement r where r.symbol = :s and r.active = true order by r.position, r.code", FormalRequirement.class)
                .setParameter("s", symbol).getResultList();
    }

    /** Wszystkie pozycje (także wycofane) – do zarządzania na S-24. */
    public List<FormalRequirement> all(DocketSymbol symbol) {
        return em.createQuery("select r from FormalRequirement r where r.symbol = :s order by r.active desc, r.position, r.code", FormalRequirement.class)
                .setParameter("s", symbol).getResultList();
    }

    public Optional<FormalRequirement> find(long id) {
        return Optional.ofNullable(em.find(FormalRequirement.class, id));
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public FormalRequirement add(DocketSymbol symbol, String code, String description, String legalBasis, RequirementCondition condition, int position, String adminId) {
        if (symbol != DocketSymbol.TS && symbol != DocketSymbol.TW) {
            throw new IllegalArgumentException("Lista kontrolna dotyczy repertoriów Ts i Tw");
        }
        String kod = code == null ? "" : code.trim().toUpperCase();
        if (!kod.matches("[A-Z0-9][A-Z0-9-]{0,19}")) {
            throw new IllegalArgumentException("Kod wymogu: wielkie litery, cyfry i myślnik (np. S-10), do 20 znaków");
        }
        validate(description, legalBasis, condition);
        Long count = em.createQuery("select count(r) from FormalRequirement r where r.symbol = :s and r.code = :c", Long.class).setParameter("s", symbol).setParameter("c", kod).getSingleResult();
        if (count > 0) {
            throw new IllegalArgumentException("Wymóg o kodzie " + kod + " już istnieje dla " + symbol.code());
        }
        FormalRequirement r = new FormalRequirement(symbol, kod, description.trim(), legalBasis.trim(), condition, position, adminId, clock.instant());
        em.persist(r);
        audit.record(adminId, "TK_ADMIN", "dodanie wymogu formalnego " + symbol.code() + " " + kod, legalBasis.trim(), null, null);
        return r;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public FormalRequirement update(long id, String description, String legalBasis, RequirementCondition condition, int position, String adminId) {
        validate(description, legalBasis, condition);
        FormalRequirement r = require(id);
        r.update(description.trim(), legalBasis.trim(), condition, position, adminId, clock.instant());
        audit.record(adminId, "TK_ADMIN", "zmiana wymogu formalnego " + r.getSymbol().code() + " " + r.getCode(), legalBasis.trim() + " · " + condition.name(), null, null);
        return r;
    }

    /** Wycofanie (albo przywrócenie) wymogu – znika z list kontrolnych nowych ocen; zapisane oceny zostają (kod stały). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN})
    public FormalRequirement setActive(long id, boolean active, String adminId) {
        FormalRequirement r = require(id);
        if (r.isActive() != active) {
            r.setActive(active, adminId, clock.instant());
            audit.record(adminId, "TK_ADMIN", (active ? "przywrócenie" : "wycofanie") + " wymogu formalnego " + r.getSymbol().code() + " " + r.getCode(), null, null, null);
        }
        return r;
    }

    public FormalRequirement require(long id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Brak wymogu " + id));
    }

    private static void validate(String description, String legalBasis, RequirementCondition condition) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Treść wymogu jest wymagana");
        }
        if (legalBasis == null || legalBasis.isBlank()) {
            throw new IllegalArgumentException("Podstawa prawna jest wymagana (np. art. 53 ust. 1 pkt 1)");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Warunek stosowania jest wymagany");
        }
    }
}
