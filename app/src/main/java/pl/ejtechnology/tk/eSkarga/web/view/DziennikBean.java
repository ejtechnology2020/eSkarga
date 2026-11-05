package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.IncomingLetterRow;

/** S-04 Dziennik wpływu (UC-01): tabela pism z filtrami daty i kanału oraz podgląd pisma w panelu bocznym. */
@Named("dziennik")
@ViewScoped
public class DziennikBean implements Serializable {

    @Inject
    private ViewData data;

    private List<IncomingLetterRow> rows;
    private IncomingLetterRow selected;
    private LocalDate od = LocalDate.of(2026, 10, 28);
    private LocalDate doDnia = DemoDataset.TODAY;
    private String kanal = "";
    private String kwalifikacja = "";
    private boolean comfy;

    @PostConstruct
    void init() {
        rows = data.incomingLetters();
        selected = rows.isEmpty() ? null : rows.get(0);   // baza dev bez pism: pusta lista, brak podglądu
    }

    public List<IncomingLetterRow> getRows() { return rows; }
    public IncomingLetterRow getSelected() { return selected; }
    public void setSelected(IncomingLetterRow selected) { this.selected = selected; }
    public LocalDate getOd() { return od; }
    public void setOd(LocalDate od) { this.od = od; }
    public LocalDate getDoDnia() { return doDnia; }
    public void setDoDnia(LocalDate doDnia) { this.doDnia = doDnia; }
    public String getKanal() { return kanal; }
    public void setKanal(String kanal) { this.kanal = kanal; }
    public String getKwalifikacja() { return kwalifikacja; }
    public void setKwalifikacja(String kwalifikacja) { this.kwalifikacja = kwalifikacja; }
    public boolean isComfy() { return comfy; }
    public void setComfy(boolean comfy) { this.comfy = comfy; }

    public String rowClass(IncomingLetterRow r) {
        if (r == selected) {
            return "sel";
        }
        return r.wiersz() == null ? "" : r.wiersz().css();
    }
}
