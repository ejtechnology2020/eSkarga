package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/** S-08 Repertorium Ts/Tw (UC-03, UC-20): księga wpisów, przełącznik Ts/Tw i rok, historia wersji wpisu skorygowanego. */
@Named("repertoriumWidok")   // nie „repertorium”: WildFly rejestruje EntityManagerFactory jednostki persystencji jako bean CDI o tej nazwie (WELD-001414)
@ViewScoped
public class RepertoriumBean implements Serializable {

    @Inject
    private ViewData data;
    @Inject
    private OidcSettings settings;

    private DocketSymbol symbol = DocketSymbol.TS;
    private int rok = 2026;
    private List<CaseSummary> rows;
    private boolean comfy;

    @PostConstruct
    void init() {
        reload();
    }

    public void reload() {
        // „Ts 319/26” to w danych przykładowych sprawa zakładana na ekranie S-07 – w demo nie pokazujemy jej jeszcze w księdze
        rows = data.cases().stream().filter(c -> c.sygnatura().symbol() == symbol && c.sygnatura().year() == rok
                && !(settings.isDemo() && c.sygnatura().format().equals("Ts 319/26")))
                .sorted((a, b) -> b.sygnatura().number() - a.sygnatura().number()).toList();
    }

    public void switchSymbol(String s) {
        symbol = DocketSymbol.valueOf(s);
        reload();
    }

    /** Historia wersji wpisu (S-08): w demo przykład korekty; z bazy – po wdrożeniu historii wersji wpisu (T-033) pusta lista. */
    public List<TimelineEntry> getHistoria() {
        if (!settings.isDemo()) {
            return List.of();
        }
        return List.of(
                new TimelineEntry(DemoDataset.dt(2026, 10, 29, 10, 12), "v2", "· skarżący: „Zofia Zmyślona” (było: „Zofia Zmyślna”) · wprowadził Marek Przykładowski, zatwierdziła Ewa Szablon", null, true, true),
                new TimelineEntry(DemoDataset.dt(2026, 10, 23, 9, 40), "v1", "· wpis pierwotny · Marek Przykładowski", null, false, true));
    }

    public String rowClass(CaseSummary c) {
        if (settings.isDemo() && "Ts 312/26".equals(c.sygnatura().format())) {
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
    /** Liczba wpisów księgi dla symbolu i roku; w demo stała z makiety (318 Ts / 14 Tw), z bazy – rzeczywista. */
    public int getTotal() { return settings.isDemo() ? (symbol == DocketSymbol.TS ? 318 : 14) : rows.size(); }
    public int getFirstRow() { return rows.isEmpty() ? 0 : Math.max(1, getTotal() - rows.size() + 1); }
}
