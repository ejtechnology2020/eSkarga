package pl.ejtechnology.tk.eSkarga.web.view;

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
    private DemoDataset demo;

    private String q = "Testowa";
    private int tab;
    private String repertorium = "Ts i Tw";
    private String rok = "2026";

    public void search() {
        // wyszukiwanie w OpenSearch + filtr CaseAccessPolicy (T-032); w demo – zestaw przykładowy
    }

    public List<CaseSummary> getCases() {
        return demo.searchCases(q);
    }

    public List<SearchHit> getHits() {
        return demo.searchHits(q);
    }

    public boolean isEmptyResult() {
        return getCases().isEmpty() && getHits().isEmpty();
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
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
    }

    public String getRok() {
        return rok;
    }

    public void setRok(String rok) {
        this.rok = rok;
    }

    public void clear() {
        q = "";
    }
}
