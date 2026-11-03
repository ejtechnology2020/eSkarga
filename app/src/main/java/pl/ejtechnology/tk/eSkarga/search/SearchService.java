package pl.ejtechnology.tk.eSkarga.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;

/**
 * Wyszukiwanie (T-032, UC-20, NFR-08): indeks zasilany przy wpisie sprawy i po przetworzeniu dokumentu (tekst OCR),
 * każdy wynik filtrowany przez {@link CaseAccessPolicy} – brak wyników ze spraw bez uprawnień. Odbudowa indeksu z bazy.
 * Dokumenty niejawne i notatki wewnętrzne nie są indeksowane.
 */
@Stateless
public class SearchService {

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    SearchIndex index;
    @Inject
    CaseAccessService access;

    public void indexCase(CaseFile cf) {
        List<Party> parties = em.createQuery("select p from Party p where p.caseFile = :c", Party.class).setParameter("c", cf).getResultList();
        var v = cf.current();
        String text = String.join(" ", nz(v.getReviewSubject()), nz(v.getReviewStandard()), nz(v.getRelatedDocket()));
        index.index(new SearchIndex.Doc(SearchIndex.Doc.caseKey(cf.getId()), "sprawa", cf.getDocket().format(), cf.getId(), null,
                cf.getDocket().format() + " – " + (cf.getDocket().symbol().name().equals("TS") ? "skarga konstytucyjna" : "wniosek"), text, null, cf.getEnteredOn(),
                parties.stream().map(Party::getDisplayName).toList()));
    }

    public void indexDocument(CaseDocument d, String text) {
        if (d.isClassified() || d.isInternalNote()) {
            return;
        }
        index.index(new SearchIndex.Doc(SearchIndex.Doc.documentKey(d.getId()), "dokument", d.getCaseFile().getDocket().format(), d.getCaseFile().getId(), d.getId(),
                d.getTitle(), text == null ? "" : text, d.pagesLabel(), d.getDocumentDate(), List.of()));
    }

    /** Wyniki widoczne dla użytkownika; sprawy bez dostępu znikają (D4). */
    public List<SearchIndex.Hit> search(String query, String userId, Set<AppRole> roles, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<SearchIndex.Hit> hits = index.search(query, limit * 3);
        Map<Long, Boolean> allowed = new HashMap<>();
        List<SearchIndex.Hit> out = new ArrayList<>();
        for (SearchIndex.Hit h : hits) {
            boolean ok = allowed.computeIfAbsent(h.caseId(), id -> {
                CaseFile cf = em.find(CaseFile.class, id);
                return cf != null && CaseAccessPolicy.canReadCase(userId, roles, access.assignment(cf));
            });
            if (ok) {
                out.add(h);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** Odbudowa z bazy (po awarii indeksu, zmianie analizatora); zwraca liczbę zaindeksowanych dokumentów. */
    public int rebuild() {
        index.clear();
        int n = 0;
        for (CaseFile cf : em.createQuery("select c from CaseFile c", CaseFile.class).getResultList()) {
            indexCase(cf);
            n++;
        }
        for (CaseDocument d : em.createQuery("select d from CaseDocument d where d.classified = false and d.internalNote = false", CaseDocument.class).getResultList()) {
            indexDocument(d, null);
            n++;
        }
        return n;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
