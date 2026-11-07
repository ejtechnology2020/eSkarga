package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.domain.model.Delegation;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Upoważnienia (T-016, FR-36, BR-10): rejestr z aktem podstawowym, weryfikacja w dacie czynności, ostrzeżenie o nakładaniu się,
 * odmowa z zdarzeniem audytu, gdy brak ważnego upoważnienia.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class DelegationService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN, AppRole.TK_PREZES})
    public Delegation add(String userId, String function, LocalDate from, LocalDate to, String basisDocRef, String adminId) {
        return add(userId, function, from, to, basisDocRef, null, null, adminId);
    }

    /** Upoważnienie z aktem podstawowym zapisanym w repozytorium dokumentów (klucz S3 + SHA-256, D5). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN, AppRole.TK_PREZES})
    public Delegation add(String userId, String function, LocalDate from, LocalDate to, String basisDocRef, String basisDocKey, String basisDocSha256, String adminId) {
        if (basisDocRef == null || basisDocRef.isBlank()) {
            throw new IllegalArgumentException("Akt podstawowy upoważnienia jest wymagany");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Podaj osobę (login z ID-users)");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("Podaj okres obowiązywania (od – do)");
        }
        Delegation d = new Delegation(userId.trim(), function, from, to, basisDocRef, adminId);
        if (basisDocKey != null) {
            d.attachBasisDocument(basisDocKey, basisDocSha256);
        }
        em.persist(d);
        audit.record(adminId, AppRole.TK_ADMIN.name(), "dodanie upoważnienia: " + function, userId + " " + from + "–" + to, null, null);
        return d;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_ADMIN, AppRole.TK_PREZES})
    public void revoke(long id, String adminId) {
        Delegation d = em.find(Delegation.class, id);
        d.revoke(clock.instant());
        audit.record(adminId, AppRole.TK_ADMIN.name(), "odwołanie upoważnienia", d.getUserId() + " " + d.getFunctionCode(), null, null);
    }

    public java.util.Optional<Delegation> byId(long id) {
        return java.util.Optional.ofNullable(em.find(Delegation.class, id));
    }

    public List<Delegation> all() {
        return em.createQuery("select d from Delegation d order by d.validFrom desc", Delegation.class).getResultList();
    }

    /** Upoważnienia do tej samej funkcji nakładające się w czasie (ostrzeżenie w S-23). */
    public List<Delegation> overlapping(Delegation d) {
        return em.createQuery("select o from Delegation o where o.functionCode = :f and o.id <> :id and o.revokedAt is null "
                        + "and o.validFrom <= :to and o.validTo >= :from", Delegation.class)
                .setParameter("f", d.getFunctionCode()).setParameter("id", d.getId()).setParameter("from", d.getValidFrom()).setParameter("to", d.getValidTo())
                .getResultList();
    }

    /** Upoważnienia wygasające w ciągu {@code days} dni. */
    public List<Delegation> expiringWithin(int days) {
        LocalDate today = clock.today();
        return em.createQuery("select d from Delegation d where d.revokedAt is null and d.validTo between :a and :b order by d.validTo", Delegation.class)
                .setParameter("a", today).setParameter("b", today.plusDays(days)).getResultList();
    }

    public Set<CaseAccessPolicy.Delegation> policyDelegations(String userId) {
        return em.createQuery("select d from Delegation d where d.userId = :u", Delegation.class).setParameter("u", userId)
                .getResultStream().map(Delegation::toPolicy).collect(Collectors.toSet());
    }

    /**
     * Czynność „za Prezesa” (BR-10): bez ważnego upoważnienia w dacie czynności → odmowa i zdarzenie audytu.
     * @throws SecurityException gdy brak upoważnienia
     */
    public void requireActingAsPresident(String userId, Set<AppRole> roles, LocalDate day, String action) {
        if (!CaseAccessPolicy.canActAsPresident(userId, roles, policyDelegations(userId), day)) {
            audit.recordDenial(userId, roles.stream().map(Enum::name).collect(Collectors.joining(",")), "odmowa: brak ważnego upoważnienia do czynności Prezesa TK",
                    action, null, null);
            throw new SecurityException("Brak ważnego upoważnienia do wykonywania obowiązków Prezesa TK w dniu " + day);
        }
    }
}
