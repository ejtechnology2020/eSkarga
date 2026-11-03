package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;


import pl.ejtechnology.tk.eSkarga.domain.Tone;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.HBar;
import pl.ejtechnology.tk.eSkarga.web.model.KpiTile;

/**
 * S-20 Raporty i statystyki (UC-19, FR-32): wpływ i załatwienia w miesiącach (p:lineChart), sposoby zakończenia (słupki),
 * czas wstępnej kontroli (mediana, p90), odsetek nadania biegu, najczęściej zaskarżane akty. Liczby przykładowe.
 */
@Named("raporty")
@ViewScoped
public class RaportyBean implements Serializable {

    /** Wiersz tabeli najczęściej zaskarżanych aktów. */
    public record Act(int miejsce, String akt, int skargi, String udzial, int nadanoBieg) implements Serializable {
    }

    @Inject
    private ViewData data;

    private String stan;
    private LocalDate od = LocalDate.of(2026, 1, 1);
    private LocalDate doDnia = LocalDate.of(2026, 10, 31);
    private String repertorium = "Ts";
    private String chartConfig;

    @PostConstruct
    void init() {
        buildChart();
    }

    public void applyState() {
        if ("brak".equals(stan)) {
            od = LocalDate.of(2027, 1, 1);
            doDnia = LocalDate.of(2027, 1, 31);
        }
    }

    public void show() {
        buildChart();
    }

    public boolean isEmptyPeriod() {
        return od.getYear() >= 2027;
    }

    /**
     * Konfiguracja Chart.js dla {@code p:chart} (PrimeFaces 15 przyjmuje JSON). Kolory z arkusza: wpływ granat,
     * załatwienia linia przerywana w tonie ostrzegawczym; wartości są też w tekście pod wykresem (dostępność).
     */
    private void buildChart() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"line\",\"data\":{\"labels\":[\"I\",\"II\",\"III\",\"IV\",\"V\",\"VI\",\"VII\",\"VIII\",\"IX\",\"X\"],");
        sb.append("\"datasets\":[{\"label\":\"Wpływ\",\"data\":").append(json(data.monthlyIntake()))
          .append(",\"borderColor\":\"#233A6B\",\"backgroundColor\":\"#233A6B\",\"borderWidth\":3,\"tension\":0.1,\"fill\":false},");
        sb.append("{\"label\":\"Załatwienia\",\"data\":").append(json(data.monthlyDisposed()))
          .append(",\"borderColor\":\"#8A5A00\",\"backgroundColor\":\"#8A5A00\",\"borderWidth\":3,\"borderDash\":[8,5],\"tension\":0.1,\"fill\":false}]},");
        sb.append("\"options\":{\"maintainAspectRatio\":false,\"plugins\":{\"legend\":{\"display\":false}},");
        sb.append("\"scales\":{\"y\":{\"beginAtZero\":true,\"max\":40,\"grid\":{\"color\":\"#D3D8E0\"}},\"x\":{\"grid\":{\"display\":false}}}}}");
        chartConfig = sb.toString();
    }

    private static String json(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]).append(i < values.length - 1 ? "," : "");
        }
        return sb.append(']').toString();
    }

    public List<KpiTile> getKpis() {
        return List.of(
                new KpiTile("318", "Wpływ (Ts)", "01.01–31.10.2026", Tone.NEUTRAL, false, null),
                new KpiTile("289", "Załatwienia", "sprawy zakończone w okresie", Tone.NEUTRAL, false, null),
                new KpiTile("38 dni", "Czas wstępnej kontroli · mediana", "90. percentyl: 74 dni", Tone.NEUTRAL, false, null),
                new KpiTile("14,2 %", "Odsetek nadania dalszego biegu", "41 z 289 załatwionych", Tone.NEUTRAL, false, null));
    }

    public List<Act> getAkty() {
        return List.of(new Act(1, "Kodeks postępowania cywilnego", 64, "20,1 %", 9), new Act(2, "Kodeks postępowania karnego", 41, "12,9 %", 5),
                new Act(3, "Ustawa o emeryturach i rentach z FUS", 22, "6,9 %", 4), new Act(4, "Ordynacja podatkowa", 18, "5,7 %", 3),
                new Act(5, "Kodeks karny wykonawczy", 15, "4,7 %", 1));
    }

    public String getDataText() {
        StringBuilder sb = new StringBuilder("Wpływ: ");
        int[] a = data.monthlyIntake();
        for (int i = 0; i < a.length; i++) {
            sb.append(a[i]).append(i < a.length - 1 ? ", " : ". ");
        }
        sb.append("Załatwienia: ");
        int[] b = data.monthlyDisposed();
        for (int i = 0; i < b.length; i++) {
            sb.append(b[i]).append(i < b.length - 1 ? ", " : ".");
        }
        return sb.toString();
    }

    public List<HBar> getOutcomes() { return data.outcomes(); }
    public String getChartConfig() { return chartConfig; }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public LocalDate getOd() { return od; }
    public void setOd(LocalDate od) { this.od = od; }
    public LocalDate getDoDnia() { return doDnia; }
    public void setDoDnia(LocalDate doDnia) { this.doDnia = doDnia; }
    public String getRepertorium() { return repertorium; }
    public void setRepertorium(String repertorium) { this.repertorium = repertorium; }
}
