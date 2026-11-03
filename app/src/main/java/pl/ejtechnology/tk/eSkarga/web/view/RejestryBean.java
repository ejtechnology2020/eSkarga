package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.AccessRequestRow;
import pl.ejtechnology.tk.eSkarga.web.model.AppealRow;
import pl.ejtechnology.tk.eSkarga.web.model.GeneralLetterRow;
import pl.ejtechnology.tk.eSkarga.web.model.PublicationItem;

/** Rejestry pomocnicze (FR-10): zażalenia (S-11), pisma ogólne (S-12), wnioski o wgląd (S-13), publikacja (S-27). */
@Named("rejestry")
@ViewScoped
public class RejestryBean implements Serializable {

    @Inject
    private DemoDataset demo;

    private boolean comfy;
    private String szablon = "pouczenie";
    private String tresc = "Szanowny Panie,\n\nw odpowiedzi na pismo z 30.10.2026 r. uprzejmie informuję, że skarga konstytucyjna musi spełniać wymogi art. 53 ustawy z dnia 30 listopada 2016 r. o organizacji i trybie postępowania przed Trybunałem Konstytucyjnym. W szczególności skargę sporządza adwokat lub radca prawny, a wnosi się ją w terminie 3 miesięcy od doręczenia ostatecznego orzeczenia (art. 77 ust. 1).\n\nZ poważaniem\nWydział Weryfikacyjny";

    public List<AppealRow> getZazalenia() { return demo.appeals(); }
    public List<GeneralLetterRow> getPismaOgolne() { return demo.generalLetters(); }
    public List<AccessRequestRow> getWnioskiOWglad() { return demo.accessRequests(); }
    public AccessRequestRow getWybranyWniosek() { return demo.accessRequests().stream().filter(AccessRequestRow::wybrany).findFirst().orElse(null); }
    public List<PublicationItem> getPublikacje() { return demo.publications(); }
    public PublicationItem getWybranaPublikacja() { return demo.publications().stream().filter(PublicationItem::wybrany).findFirst().orElse(null); }
    public boolean isComfy() { return comfy; }
    public void setComfy(boolean comfy) { this.comfy = comfy; }
    public String getSzablon() { return szablon; }
    public void setSzablon(String szablon) { this.szablon = szablon; }
    public String getTresc() { return tresc; }
    public void setTresc(String tresc) { this.tresc = tresc; }
}
