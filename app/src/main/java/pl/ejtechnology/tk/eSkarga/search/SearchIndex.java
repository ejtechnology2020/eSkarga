package pl.ejtechnology.tk.eSkarga.search;

import java.time.LocalDate;
import java.util.List;

/**
 * Indeks pełnotekstowy spraw i dokumentów (T-032, UC-20, FR-31). Implementacje: OpenSearch (analizator polski) i pamięciowa
 * (testy, tryb demo). Indeks przechowuje tylko to, co jest potrzebne do wyszukania – uprawnienia sprawdza aplikacja
 * po stronie wyników ({@link SearchService}), nigdy indeks.
 */
public interface SearchIndex {

    /** Dokument indeksu: sprawa ({@code documentId == null}) albo dokument akt. */
    record Doc(String id, String type, String docket, long caseId, Long documentId, String title, String text, String pages, LocalDate date, List<String> parties) {
        public static String caseKey(long caseId) {
            return "sprawa-" + caseId;
        }

        public static String documentKey(long documentId) {
            return "dokument-" + documentId;
        }
    }

    /** Trafienie z fragmentem i wynikiem. */
    record Hit(String id, String type, String docket, long caseId, Long documentId, String title, String pages, LocalDate date, String fragment, double score) {
    }

    void index(Doc doc);

    void delete(String id);

    List<Hit> search(String query, int limit);

    void clear();

    boolean available();
}
