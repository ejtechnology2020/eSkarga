package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;

/** Szybkie wyszukiwanie sygnatury w górnym pasku: poprawna sygnatura otwiera akta, inny tekst – wyszukiwarkę. */
@Named("quickSearch")
@RequestScoped
public class QuickSearchBean implements Serializable {

    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String go() {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            DocketNumber n = DocketNumber.parse(query);
            return "/akta/podglad.xhtml?faces-redirect=true&sygnatura=" + n.format().replace(" ", "%20");
        } catch (IllegalArgumentException e) {
            return "/wyszukiwarka.xhtml?faces-redirect=true&q=" + query.trim();
        }
    }
}
