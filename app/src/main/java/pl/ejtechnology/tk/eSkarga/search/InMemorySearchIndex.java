package pl.ejtechnology.tk.eSkarga.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Indeks w pamięci: dopasowanie słów po normalizacji (małe litery, bez znaków diakrytycznych, prefiksy ≥ 4 znaki). */
public final class InMemorySearchIndex implements SearchIndex {

    private final Map<String, Doc> docs = new ConcurrentHashMap<>();

    @Override
    public void index(Doc doc) {
        docs.put(doc.id(), doc);
    }

    @Override
    public void delete(String id) {
        docs.remove(id);
    }

    @Override
    public List<Hit> search(String query, int limit) {
        String[] terms = norm(query).split("\\s+");
        List<Hit> out = new ArrayList<>();
        for (Doc d : docs.values()) {
            String title = norm(d.title());
            String text = norm(d.text());
            String docket = norm(d.docket());
            String parties = norm(String.join(" ", d.parties() == null ? List.of() : d.parties()));
            double score = 0;
            for (String t : terms) {
                if (t.isBlank()) {
                    continue;
                }
                if (docket.contains(t)) {
                    score += 5;
                }
                if (title.contains(t)) {
                    score += 3;
                }
                if (parties.contains(t)) {
                    score += 2;
                }
                if (text.contains(t)) {
                    score += 1;
                }
            }
            if (score > 0) {
                out.add(new Hit(d.id(), d.type(), d.docket(), d.caseId(), d.documentId(), d.title(), d.pages(), d.date(), fragment(d.text(), terms[0]), score));
            }
        }
        out.sort(Comparator.comparingDouble(Hit::score).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    @Override
    public void clear() {
        docs.clear();
    }

    @Override
    public boolean available() {
        return true;
    }

    public int size() {
        return docs.size();
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ł', 'l');
        return n;
    }

    static String fragment(String text, String term) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int idx = norm(text).indexOf(term);
        if (idx < 0) {
            return text.substring(0, Math.min(160, text.length()));
        }
        int from = Math.max(0, idx - 60);
        int to = Math.min(text.length(), idx + 100);
        return (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
    }
}
