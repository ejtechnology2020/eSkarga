package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/** S-08 Repertorium Ts/Tw (UC-03, UC-20): księga wpisów, przełącznik Ts/Tw i rok, historia wersji wpisu skorygowanego. */
@Named("repertorium")
@ViewScoped
public class RepertoriumBean implements Serializable {

    @Inject
    private DemoDataset demo;

    private DocketSymbol symbol = DocketSymbol.TS;
    private int rok = 2026;
    private List<CaseSummary> rows;
    private boolean comfy;

    @PostConstruct
    void init() {
        reload();
    }

    public void reload() {
        rows = demo.cases().stream().filter(c -> c.sygnatura().symbol() == symbol && c.sygnatura().year() == rok
                && !c.sygnatura().format().equals("Ts 319/26")).sorted((a, b) -> b.sygnatura().number() - a.sygnatura().number()).toList();
    }

    public void switchSymbol(String s) {
        symbol = DocketSymbol.valueOf(s);
        reload();
    }

    public List<TimelineEntry> getHistoria() {
        return List.of(
                new TimelineEntry(DemoDataset.dt(2026, 10, 29, 10, 12), "v2", "· skarżący: „Zofia Zmyślona” (było: „Zofia Zmyślna”) · wprowadził Marek Przykładowski, zatwierdziła Ewa Szablon", null, true, true),
                new TimelineEntry(DemoDataset.dt(2026, 10, 23, 9, 40), "v1", "· wpis pierwotny · Marek Przykładowski", null, false, true));
    }

    public String rowClass(CaseSummary c) {
        if ("Ts 312/26".equals(c.sygnatura().format())) {
            return "sel";
        }
        return c.status().isReadOnly() && c.status().name().contains("ODMOWA") ? "off" : "";
    }

    public List<CaseSummary> getRows() { return rows; }
    public DocketSymbol getSymbol() { return symbol; }
    public int getRok() { return rok; }
    public void setRok(int rok) { this.rok = rok; reload(); }
    public boolean isComfy() { return comfy; }
    public void setComfy(boolean comfy) { this.comfy = comfy; }
    public int getTotal() { return symbol == DocketSymbol.TS ? 318 : 14; }
}
