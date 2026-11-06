package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.ChecklistState;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.ChecklistEntry;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Lista kontrolna wymogów formalnych (UC-06, FR-11): ocena wymogu z tabeli decyzyjnej zapisywana jako nowy wiersz (NFR-01),
 * obowiązuje najnowsza. Oceniać może zespół sędziego (sędzia, asystent) i referent / kierownik wydziału z dostępem do akt (D4);
 * każda ocena to zdarzenie audytu z sygnaturą (NFR-02).
 */
@Stateless
public class ChecklistService {

    static final Set<AppRole> ROLES = Set.of(AppRole.TK_SEDZIA, AppRole.TK_ASYSTENT, AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK);

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    CaseAccessService access;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ChecklistEntry record(long caseId, String requirementCode, ChecklistState state, String note, String pageRef, String userId, Set<AppRole> roles) {
        CaseFile cf = cases.require(caseId);
        if (roles == null || roles.stream().noneMatch(ROLES::contains) || !access.canRead(cf, userId, roles)) {
            audit.recordDenial(userId, roles == null ? null : roles.toString(), "odmowa: ocena listy kontrolnej poza rolą", requirementCode, cf.getDocket().format(), null);
            throw new SecurityException("Ocena listy kontrolnej wymaga roli zespołu sędziego, referenta albo kierownika z dostępem do akt");
        }
        if (requirementCode == null || requirementCode.isBlank() || state == null) {
            throw new IllegalArgumentException("Podaj kod wymogu i ocenę");
        }
        if (cf.current().getStatus().isReadOnly()) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " jest tylko do odczytu (BR-08)");
        }
        ChecklistEntry e = new ChecklistEntry(cf, requirementCode.trim(), state, blankToNull(note, 500), blankToNull(pageRef, 40), userId, clock.instant());
        em.persist(e);
        audit.record(userId, roles.stream().filter(ROLES::contains).findFirst().map(Enum::name).orElse(null),
                "lista kontrolna: " + requirementCode + " → " + state.name(), blankToNull(note, 80), cf.getDocket().format(), null);
        return e;
    }

    /** Obowiązujące oceny sprawy: najnowszy wiersz dla każdego kodu wymogu. */
    public Map<String, ChecklistEntry> current(CaseFile cf) {
        Map<String, ChecklistEntry> out = new LinkedHashMap<>();
        for (ChecklistEntry e : em.createQuery("select e from ChecklistEntry e where e.caseFile = :c order by e.id", ChecklistEntry.class).setParameter("c", cf).getResultList()) {
            out.put(e.getRequirementCode(), e);
        }
        return out;
    }

    private static String blankToNull(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
