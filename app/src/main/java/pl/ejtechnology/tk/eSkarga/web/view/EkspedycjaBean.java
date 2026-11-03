package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DeliveryMethod;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineCalculator;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineComputation;
import pl.ejtechnology.tk.eSkarga.domain.deadline.PolishHolidays;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.DeliveryRow;
import pl.ejtechnology.tk.eSkarga.web.model.Effect;

/**
 * S-10 Ekspedycja i rejestr doręczeń (UC-08, FR-25, FR-26). Zakładki: Do wysłania, Wysłane bez potwierdzenia, Doręczone.
 * Rejestracja zwrotki pokazuje termin, który ruszy po zapisie (DeadlineCalculator, BR-02); korekta daty doręczenia
 * przelicza termin i wymaga uzasadnienia (T-041).
 */
@Named("ekspedycja")
@ViewScoped
public class EkspedycjaBean implements Serializable {

    @Inject
    private DemoDataset demo;

    private String stan;
    private int tab = 1;
    private LocalDate dataDoreczenia = LocalDate.of(2026, 11, 2);
    private DeliveryMethod sposob = DeliveryMethod.DO_RAK;
    private boolean korekta;
    private String uzasadnienie = "Na UPD widnieje 03.11.2026 00:12, nie 02.11.2026.";
    private boolean dialog;

    public void applyState() {
        switch (stan == null ? "" : stan) {
            case "wysylka" -> tab = 0;
            case "okno" -> { tab = 0; dialog = true; }
            case "korekta" -> { tab = 1; korekta = true; dataDoreczenia = LocalDate.of(2026, 11, 3); }
            case "doreczone" -> tab = 2;
            default -> tab = 1;
        }
    }

    /** Termin 7 dni na braki od daty doręczenia (art. 61 ust. 3) – przelicza się przy każdej zmianie daty. */
    public DeadlineComputation getTermin() {
        return new DeadlineCalculator(new PolishHolidays(demo.extraDaysOff(2026))).sevenDaysFromDelivery(dataDoreczenia);
    }

    public void dateChanged() {
        korekta = !dataDoreczenia.equals(LocalDate.of(2026, 11, 2));
    }

    public List<Effect> getSkutkiWysylki() {
        return List.of(Effect.warning("Wysyłki nie można cofnąć. Wpis trafi do książki nadawczej."),
                Effect.next("To zawiadomienie nie uruchamia terminu."));
    }

    public List<DeliveryRow> getDoWyslania() { return demo.toSend(); }
    public List<DeliveryRow> getBezPotwierdzenia() { return demo.unconfirmed(); }
    public List<DeliveryRow> getDoreczone() { return demo.delivered(); }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public int getTab() { return tab; }
    public void setTab(int tab) { this.tab = tab; }
    public LocalDate getDataDoreczenia() { return dataDoreczenia; }
    public void setDataDoreczenia(LocalDate d) { this.dataDoreczenia = d; }
    public DeliveryMethod getSposob() { return sposob; }
    public void setSposob(DeliveryMethod sposob) { this.sposob = sposob; }
    public boolean isKorekta() { return korekta; }
    public String getUzasadnienie() { return uzasadnienie; }
    public void setUzasadnienie(String uzasadnienie) { this.uzasadnienie = uzasadnienie; }
    public boolean isDialog() { return dialog; }
    public void setDialog(boolean dialog) { this.dialog = dialog; }
    public DeliveryMethod[] getSposoby() { return DeliveryMethod.values(); }
}
