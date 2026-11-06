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
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Highlight;
import pl.ejtechnology.tk.eSkarga.domain.model.InternalNote;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;

/**
 * Notatki wewnętrzne i zaznaczenia (T-035, FR-24): widoczne tylko dla zespołu sędziego ({@link CaseAccessPolicy#canReadInternalNotes}),
 * nigdy w eksporcie akt jawnych ani w widoku referenta. Kopiowanie z cytatem: „Ts 312/26, k. 3”.
 */
@Stateless
public class NoteService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseAccessService access;
    @Inject
    AuditService audit;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public InternalNote addNote(long caseId, String title, String body, String userId, Set<AppRole> roles) {
        CaseFile cf = em.find(CaseFile.class, caseId);
        requireTeam(cf, userId, roles);
        InternalNote n = new InternalNote(cf, userId, title, body);
        em.persist(n);
        audit.record(userId, null, "dodanie notatki wewnętrznej", title, cf.getDocket().format(), null);
        return n;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Highlight addHighlight(long documentId, int page, String geometryJson, String comment, String userId, Set<AppRole> roles) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        requireTeam(doc.getCaseFile(), userId, roles);
        Highlight h = new Highlight(doc, userId, page, geometryJson, comment);
        em.persist(h);
        return h;
    }

    public List<InternalNote> notes(long caseId, String userId, Set<AppRole> roles) {
        CaseFile cf = em.find(CaseFile.class, caseId);
        if (!canRead(cf, userId, roles)) {
            return List.of();
        }
        return em.createQuery("select n from InternalNote n where n.caseFile = :c order by n.createdAt desc", InternalNote.class).setParameter("c", cf).getResultList();
    }

    public List<Highlight> highlights(long documentId, String userId, Set<AppRole> roles) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        if (!canRead(doc.getCaseFile(), userId, roles)) {
            return List.of();
        }
        return em.createQuery("select h from Highlight h where h.document = :d order by h.pageNo, h.id", Highlight.class).setParameter("d", doc).getResultList();
    }

    /** Cytat do kopiowania (brief §6): sygnatura i karta. */
    public static String citation(CaseFile cf, CaseDocument doc, int pageInDocument) {
        int karta = (doc.getPageFrom() == null ? 1 : doc.getPageFrom()) + Math.max(0, pageInDocument - 1);
        return cf.getDocket().format() + ", k. " + karta;
    }

    public boolean canRead(CaseFile cf, String userId, Set<AppRole> roles) {
        return CaseAccessPolicy.canReadInternalNotes(userId, roles, access.assignment(cf));
    }

    private void requireTeam(CaseFile cf, String userId, Set<AppRole> roles) {
        if (!canRead(cf, userId, roles)) {
            audit.recordDenial(userId, null, "odmowa: notatki wewnętrzne poza zespołem sędziego", null, cf.getDocket().format(), null);
            throw new SecurityException("Notatki wewnętrzne są dostępne dla zespołu sędziego");
        }
    }
}
