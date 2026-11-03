package pl.ejtechnology.tk.eSkarga.domain.service;

import java.util.List;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.CorrectionStatus;
import pl.ejtechnology.tk.eSkarga.security.AppRole;

/**
 * Korekta wpisu repertorium (T-014, UC-22, FR-08): wniosek referenta z uzasadnieniem tworzy wersję OCZEKUJE;
 * kierownik zatwierdza (wersja staje się obowiązująca) albo odrzuca. Poprzednie wersje nigdy nie są zmieniane ani usuwane.
 */
@Stateless
public class CorrectionService {

    /** Zmieniane pola wpisu. */
    public record Changes(String reviewSubject, String reviewStandard, String relatedDocket) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public CaseFileVersion request(long caseId, Changes changes, String justification, String userId) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Uzasadnienie korekty jest wymagane");
        }
        CaseFile cf = em.find(CaseFile.class, caseId);
        if (hasPending(cf)) {
            throw new IllegalStateException("Dla wpisu " + cf.getDocket().format() + " czeka już korekta do akceptacji");
        }
        CaseFileVersion v = cf.current().next(cf.current().getStatus(), userId);
        CaseFileService.reNumber(cf, v);
        if (changes.reviewSubject() != null) {
            v.setReviewSubject(changes.reviewSubject());
        }
        if (changes.reviewStandard() != null) {
            v.setReviewStandard(changes.reviewStandard());
        }
        if (changes.relatedDocket() != null) {
            v.setRelatedDocket(changes.relatedDocket());
        }
        v.setJustification(justification);
        v.setCorrectionStatus(CorrectionStatus.OCZEKUJE);
        em.persist(cf.addVersion(v));
        audit.record(userId, AppRole.TK_REFERENT.name(), "wniosek o korektę wpisu", "v" + v.getVersion(), cf.getDocket().format(), null);
        return v;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void approve(long versionId, String approverId, Set<AppRole> roles) {
        CaseFileVersion v = pending(versionId);
        if (!roles.contains(AppRole.TK_KIEROWNIK)) {
            audit.recordDenial(approverId, null, "odmowa: akceptacja korekty bez roli kierownika", "v" + v.getVersion(), v.getCaseFile().getDocket().format(), null);
            throw new SecurityException("Korektę zatwierdza kierownik wydziału");
        }
        if (approverId.equals(v.getCreatedBy())) {
            throw new IllegalArgumentException("Korekty nie może zatwierdzić jej autor");
        }
        v.setApprovedBy(approverId);
        v.setCorrectionStatus(CorrectionStatus.ZATWIERDZONA);
        audit.record(approverId, AppRole.TK_KIEROWNIK.name(), "zatwierdzenie korekty", "v" + v.getVersion(), v.getCaseFile().getDocket().format(), null);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void reject(long versionId, String approverId, String remarks) {
        if (remarks == null || remarks.isBlank()) {
            throw new IllegalArgumentException("Odrzucenie wymaga uwag kierownika");
        }
        CaseFileVersion v = pending(versionId);
        v.setApprovedBy(approverId);
        v.setJustification(v.getJustification() + " | Odrzucono: " + remarks);
        v.setCorrectionStatus(CorrectionStatus.ODRZUCONA);
        audit.record(approverId, AppRole.TK_KIEROWNIK.name(), "odrzucenie korekty", "v" + v.getVersion(), v.getCaseFile().getDocket().format(), null);
    }

    public List<CaseFileVersion> history(long caseId) {
        return em.createQuery("select v from CaseFileVersion v where v.caseFile.id = :c order by v.version", CaseFileVersion.class)
                .setParameter("c", caseId).getResultList();
    }

    public List<CaseFileVersion> pendingAll() {
        return em.createQuery("select v from CaseFileVersion v where v.correctionStatus = :s order by v.createdAt", CaseFileVersion.class)
                .setParameter("s", CorrectionStatus.OCZEKUJE).getResultList();
    }

    private boolean hasPending(CaseFile cf) {
        return cf.getVersions().stream().anyMatch(v -> v.getCorrectionStatus() == CorrectionStatus.OCZEKUJE);
    }

    private CaseFileVersion pending(long versionId) {
        CaseFileVersion v = em.find(CaseFileVersion.class, versionId);
        if (v == null || v.getCorrectionStatus() != CorrectionStatus.OCZEKUJE) {
            throw new IllegalStateException("Brak korekty oczekującej o id " + versionId);
        }
        return v;
    }
}
