package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DeadlineState;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.Tone;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;
import pl.ejtechnology.tk.eSkarga.web.model.HBar;
import pl.ejtechnology.tk.eSkarga.web.model.KpiTile;

/** S-19 Monitor terminów i zaległości (UC-18): kafelki, sprawy wymagające uwagi z przyczyną i osobą, obciążenie zespołu. */
@Named("monitor")
@ViewScoped
public class MonitorBean implements Serializable {

    /** Wiersz tabeli spraw wymagających uwagi. */
    public record Row(String id, String sprawa, boolean sygnatura, String przyczyna, DeadlineView termin, String odpowiedzialny,
                      String odpUwaga, boolean przekaz, Tone wiersz) implements Serializable {
    }

    @Inject
    private ViewData data;

    public List<KpiTile> getKpis() {
        return List.of(
                new KpiTile("1", "Terminy w ciągu 2 dni", "Ts 301/26 · zażalenie do 04.11.2026 (jutro)", Tone.WARN, false, "#t"),
                new KpiTile("1", "Po terminie bez czynności", "kwalifikacja pisma DW 2026/04805 · 4 dni", Tone.ERR, false, "#t"),
                new KpiTile("1", "Brak zwrotki ponad 14 dni", "Ts 315/26 · 18 dni", Tone.ERR, false, "#t"),
                new KpiTile("2", "Sprawy bez wyznaczonego sędziego", "najdłużej: Ts 318/26 · 6 dni", Tone.NEUTRAL, false, "#t"));
    }

    public List<Row> getRows() {
        return List.of(
                new Row("1", "DW 2026/04805", false, "Pismo niezakwalifikowane ponad 1 dzień",
                        new DeadlineView(DeadlineType.OCZEKIWANIE, DeadlineState.OVERDUE, null, null, "4 dni · 3 dni po terminie", false), "Marek Przykładowski", null, true, Tone.ERR),
                new Row("2", "Ts 315/26", true, "Brak zwrotki ponad 14 dni",
                        new DeadlineView(DeadlineType.ZWROTKA, DeadlineState.OVERDUE, null, null, "18 dni · 4 dni po terminie", false), "Marek Przykładowski", null, true, Tone.ERR),
                new Row("3", "Ts 301/26", true, "Termin na zażalenie", data.caseBy("Ts 301/26").termin(), "Marek Przykładowski", null, true, Tone.WARN),
                new Row("4", "Ts 318/26", true, "Oczekuje na wyznaczenie sędziego", DeadlineView.none("6 dni"), "sędzia TK Maria Makietowa", "(Prezes TK)", false, Tone.NEUTRAL),
                new Row("5", "Ts 319/26", true, "Oczekuje na wyznaczenie sędziego", DeadlineView.none("0 dni"), "sędzia TK Maria Makietowa", "(Prezes TK)", false, Tone.NEUTRAL));
    }

    public List<HBar> getReferenci() { return data.clerkLoads(); }
    public List<HBar> getSedziowie() { return data.judgeBars(); }
}
