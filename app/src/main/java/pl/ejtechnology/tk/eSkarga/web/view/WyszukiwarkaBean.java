package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.SearchHit;

/**
 * S-02 Wyszukiwarka spraw i dokumentów (UC-20, FR-31). Wyniki obejmują tylko sprawy, do których użytkownik ma
 * dostęp; informacja o pominiętych sprawach nie jest pokazywana, bo uprawnienia nie mogą ujawniać istnienia spraw.
 */
@Named("wyszukiwarka")
@ViewScoped
public class WyszukiwarkaBean implements Serializable {

    @Inject
    private ViewData data;

    // formularz startuje pusty (bez danych przykładowych); zapytanie może przyjść z paska górnego (?q=)
    private String q = "";
    private int tab;
    private String repertorium = "Ts i Tw";
    private String rok = "";
    private List<CaseSummary> cases;
    private List<SearchHit> hits;

    /** Wyszukiwanie w indeksie (OpenSearch) z filtrem dostępu, z zapasem w bazie (JpaViewData.searchCases); w demo – zestaw przykładowy. */
    public void search() {
        cases = null;
        hits = null;
    }

    /** Czy jest co pokazać: zapytanie niepuste (pusty formularz nie pokazuje „brak wyników”). */
    public boolean isSearched() {
        return q != null && !q.isBlank();
    }

    public List<CaseSummary> getCases() {
        if (cases == null) {
            cases = isSearched() ? data.searchCases(q.trim()).stream().filter(this::matchesFilters).toList() : List.of();
        }
        return cases;
    }

    public List<SearchHit> getHits() {
        if (hits == null) {
            hits = isSearched() ? data.searchHits(q.trim()).stream().filter(h -> h.sprawa() == null || matchesFilters(h.sprawa())).toList() : List.of();
        }
        return hits;
    }

    /** Liczba spraw, w których są trafienia w dokumentach (nagłówek zakładki „Dokumenty”). */
    public long getHitCases() {
        return getHits().stream().map(SearchHit::sprawa).filter(java.util.Objects::nonNull).distinct().count();
    }

    public boolean isEmptyResult() {
        return isSearched() && getCases().isEmpty() && getHits().isEmpty();
    }

    private boolean matchesFilters(CaseSummary c) {
        return matchesFilters(c.sygnatura());
    }

    private boolean matchesFilters(pl.ejtechnology.tk.eSkarga.domain.DocketNumber dn) {
        if (dn == null) {
            return true;
        }
        boolean symbolOk = repertorium == null || repertorium.isBlank() || repertorium.equals("Ts i Tw") || dn.symbol().code().equalsIgnoreCase(repertorium);
        boolean yearOk = rok == null || rok.isBlank() || String.valueOf(dn.year()).equals(rok);
        return symbolOk && yearOk;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        if (q == null || !q.equals(this.q)) {
            cases = null;
            hits = null;
        }
        this.q = q;
    }

    public int getTab() {
        return tab;
    }

    public void setTab(int tab) {
        this.tab = tab;
    }

    public String getRepertorium() {
        return repertorium;
    }

    public void setRepertorium(String repertorium) {
        this.repertorium = repertorium;
        cases = null;
        hits = null;
    }

    public String getRok() {
        return rok;
    }

    public void setRok(String rok) {
        this.rok = rok;
        cases = null;
        hits = null;
    }

    public void clear() {
        q = "";
    }
}
