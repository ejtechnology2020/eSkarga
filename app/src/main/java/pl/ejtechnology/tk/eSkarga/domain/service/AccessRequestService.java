package pl.ejtechnology.tk.eSkarga.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
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
import pl.ejtechnology.tk.eSkarga.domain.model.AccessRequest;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Publication;
import pl.ejtechnology.tk.eSkarga.domain.model.PublicationStatus;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Wgląd w akta i publikacja (T-036, UC-15, UC-16, FR-22, FR-27, FR-29, BR-09). Osoba trzecia dostaje wyłącznie wersje zanonimizowane,
 * dokumenty niejawne nie są udostępniane nikomu przez system; dokument z flagą art. 80 nie może zostać opublikowany bez wersji zanonimizowanej.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class AccessRequestService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK})
    public AccessRequest register(long caseId, String requester, boolean participant, String scope, BigDecimal fee, String userId) {
        CaseFile cf = em.find(CaseFile.class, caseId);
        AccessRequest r = new AccessRequest(cf, requester, participant, scope, fee);
        em.persist(r);
        audit.record(userId, "TK_REFERENT", "rejestracja wniosku o wgląd (" + (participant ? "uczestnik" : "osoba trzecia") + ")", requester, cf.getDocket().format(), null);
        return r;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK})
    public void schedule(long requestId, Instant at, String userId) {
        AccessRequest r = em.find(AccessRequest.class, requestId);
        r.scheduleReadingRoom(at);
        audit.record(userId, "TK_REFERENT", "wyznaczenie terminu w czytelni", r.getRequester(), r.getCaseFile().getDocket().format(), null);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK})
    public void anonymisedReady(long requestId, String userId) {
        AccessRequest r = em.find(AccessRequest.class, requestId);
        r.anonymisedReady();
        audit.record(userId, "TK_REFERENT", "wersja zanonimizowana gotowa", r.getRequester(), r.getCaseFile().getDocket().format(), null);
    }

    /**
     * Dokumenty widoczne dla wnioskodawcy (BR-09): uczestnik – akta jawne bez notatek; osoba trzecia – tylko dokumenty z wersją
     * zanonimizowaną; nikt – dokumentów niejawnych. Zwraca klucz pliku do wydania.
     */
    public List<String> visibleFileKeys(AccessRequest r) {
        List<CaseDocument> docs = em.createQuery("select d from CaseDocument d where d.caseFile = :c order by d.pageFrom", CaseDocument.class)
                .setParameter("c", r.getCaseFile()).getResultList();
        return docs.stream().filter(d -> !d.isClassified() && !d.isInternalNote())
                .map(d -> r.isParticipant() ? d.getStorageKey() : d.getAnonymisedKey())
                .filter(k -> k != null).toList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK})
    public Publication queueForPublication(long documentId, String userId) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        Publication p = new Publication(doc);
        em.persist(p);
        audit.record(userId, "TK_REFERENT", "skierowanie do publikacji", doc.getTitle(), doc.getCaseFile().getDocket().format(), null);
        return p;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_KIEROWNIK})
    public void approvePublication(long publicationId, String userId) {
        Publication p = em.find(Publication.class, publicationId);
        boolean art80 = p.getDocument().getCaseFile().current().isFlagAnonymise();
        p.approve(userId, clock.instant(), art80);
        audit.record(userId, "TK_REFERENT", "zatwierdzenie anonimizacji", p.getDocument().getTitle(), p.getDocument().getCaseFile().getDocket().format(), null);
    }

    public List<AccessRequest> all() {
        return em.createQuery("select r from AccessRequest r order by r.createdAt desc", AccessRequest.class).getResultList();
    }

    public List<Publication> pendingPublications() {
        return em.createQuery("select p from Publication p where p.status in (:s) order by p.id", Publication.class)
                .setParameter("s", List.of(PublicationStatus.OCZEKUJE, PublicationStatus.POPRAWKA)).getResultList();
    }
}
