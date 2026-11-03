package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;

/** Fasada {@link CaseAccessPolicy} nad przydziałami z bazy (D4). Każde zapytanie o sprawę albo dokument przechodzi tędy. */
@Stateless
public class CaseAccessService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;

    public CaseAccessPolicy.CaseAssignment assignment(CaseFile cf) {
        List<CaseAssignment> active = em.createQuery("select a from CaseAssignment a where a.caseFile = :c and a.releasedOn is null", CaseAssignment.class)
                .setParameter("c", cf).getResultList();
        String judge = active.stream().filter(a -> a.getRole() == AssignmentRole.JUDGE).map(CaseAssignment::getUserId).findFirst().orElse(null);
        Set<String> team = active.stream().filter(a -> a.getRole() == AssignmentRole.TEAM).map(CaseAssignment::getUserId).collect(Collectors.toSet());
        Set<String> clerks = active.stream().filter(a -> a.getRole() == AssignmentRole.CLERK).map(CaseAssignment::getUserId).collect(Collectors.toSet());
        return new CaseAccessPolicy.CaseAssignment(judge, team, clerks);
    }

    /** Odczyt akt z zapisem odmowy w dzienniku zdarzeń. */
    public boolean canRead(CaseFile cf, String userId, Set<AppRole> roles) {
        boolean ok = CaseAccessPolicy.canReadCase(userId, roles, assignment(cf));
        if (!ok) {
            audit.recordDenial(userId, roles.stream().map(Enum::name).collect(Collectors.joining(",")), "odmowa dostępu do sprawy", null, cf.getDocket().format(), null);
        }
        return ok;
    }

    /** Raport przeglądu uprawnień (NFR-04): aktywne przydziały. */
    public List<CaseAssignment> activeAssignments() {
        return em.createQuery("select a from CaseAssignment a where a.releasedOn is null order by a.userId, a.caseFile.id", CaseAssignment.class).getResultList();
    }
}
