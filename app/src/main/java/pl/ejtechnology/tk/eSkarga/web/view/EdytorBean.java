package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.ChecklistItem;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/**
 * S-15 Edytor projektu pisma (UC-07, UC-10, UC-12, FR-15, FR-16, D13). Z lewej dane pisma (rodzaj, podstawa prawna
 * z art. 61 ust. 4 pkt 1–3, braki z listy kontrolnej), z prawej podgląd PDF na żywo ze znakiem wodnym „Projekt”.
 * Stany: asystent edytuje, brak podstawy (błąd), sędzia akceptuje, projekt zablokowany, skład 3 sędziów.
 */
@Named("edytor")
@ViewScoped
public class EdytorBean implements Serializable {

    @Inject
    private DemoDataset demo;
    @Inject
    private CurrentUser currentUser;

    private String stan;
    private String rodzaj = "odmowa";
    private String podstawa = "pkt1";
    private boolean brakPelnomocnictwo = true;
    private boolean brakUzasadnienie;
    private String uwaga;

    public void applyState() {
        if (stan == null) {
            AppRole r = currentUser.getRole();
            stan = r == AppRole.TK_SEDZIA || r == AppRole.TK_PREZES ? "sedzia" : "asystent";
        }
        if ("blad".equals(stan)) {
            podstawa = null;
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Nie można przesłać projektu sędziemu.", "Wybierz podstawę prawną odmowy — art. 61 ust. 4 pkt 1, 2 albo 3."));
        }
    }

    public void sendToJudge() {
        if (podstawa == null || podstawa.isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Nie można przesłać projektu sędziemu.", "Wybierz podstawę prawną odmowy — art. 61 ust. 4 pkt 1, 2 albo 3."));
            return;
        }
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Projekt przesłany sędziemu.", "Wersja v3 z 3 · 03.11.2026 09:02."));
    }

    public boolean isSklad() { return "sklad".equals(stan); }
    public boolean isSedzia() { return "sedzia".equals(stan) || isSklad(); }
    public boolean isLock() { return "lock".equals(stan); }
    public boolean isAsystent() { return !isSedzia() && !isLock(); }
    public boolean isReadOnly() { return isLock() || isSedzia(); }

    public String getSygnatura() { return isSklad() ? "Ts 276/26" : "Ts 312/26"; }
    public String getRodzajLabel() { return isSklad() ? "Postanowienie w sprawie zażalenia" : "Postanowienie o odmowie nadania dalszego biegu"; }
    public String getOpis() { return isSklad() ? "skład trzech sędziów · posiedzenie 12.11.2026" : "projekt warunkowy — na wypadek, gdyby pismo z 03.11.2026 nie usunęło braku"; }
    public String getEyebrow() { return isLock() ? "tylko do odczytu" : isSklad() ? "skład trzech sędziów" : isSedzia() ? "widok sędziego" : "widok asystenta"; }
    public String getWersja() { return isSklad() ? "v3 z 3" : "v2 z 3"; }
    public String getSkladText() { return isSklad() ? "Ewa Wzorcowa — przewodnicząca, Adam Testowy — sprawozdawca, Maria Makietowa" : "sędzia TK Piotr Fikcyjny"; }
    public String getPrzedmiot() { return isSklad() ? "zażalenia na postanowienie o odmowie nadania dalszego biegu skardze konstytucyjnej" : "skargi konstytucyjnej Jana Przykładowego w sprawie zgodności art. 505¹ § 1 k.p.c. z art. 45 ust. 1 i art. 78 Konstytucji"; }
    public String getSentencja() { return isSklad() ? "nie uwzględnić zażalenia." : "odmówić nadania dalszego biegu skardze konstytucyjnej."; }
    public String getUzasadnienie() {
        return isSklad() ? "Zażalenie wniesiono w terminie. Skład podziela ocenę, że skarga nie spełnia wymogów art. 53 ust. 1 pkt 3 ustawy…"
                : "Pełnomocnictwo przedłożone 03.11.2026 r. zostało udzielone 30.10.2026 r., a więc po wniesieniu skargi (13.10.2026 r.). Nie obejmuje ono umocowania do sporządzenia skargi, co stanowi brak formalny w rozumieniu art. 61 ust. 4 pkt 1 ustawy.";
    }
    public String getTermin() { return isSklad() ? "nie dotyczy" : "nie dotyczy (zażalenie: 7 dni od doręczenia)"; }
    public String getUwagiCnt() { return isSklad() ? "0 otwartych" : "1 otwarta"; }

    public List<TimelineEntry> getUwagi() {
        return List.of(
                new TimelineEntry(DemoDataset.dt(2026, 11, 2, 17, 5), "sędzia TK Piotr Fikcyjny:", "„Wskaż datę pełnomocnictwa wprost w uzasadnieniu.” <span class=\"tag ok\">uwzględniono w v2</span>", null, true, true),
                new TimelineEntry(DemoDataset.dt(2026, 11, 2, 17, 7), "sędzia TK Piotr Fikcyjny:", "„Poczekajmy z decyzją na ocenę pisma z 03.11.” <span class=\"tag\">otwarta</span>", null, false, true));
    }

    public List<ChecklistItem> getBraki() { return demo.checklist("Ts 312/26"); }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getRodzaj() { return rodzaj; }
    public void setRodzaj(String rodzaj) { this.rodzaj = rodzaj; }
    public String getPodstawa() { return podstawa; }
    public void setPodstawa(String podstawa) { this.podstawa = podstawa; }
    public boolean isBrakPelnomocnictwo() { return brakPelnomocnictwo; }
    public void setBrakPelnomocnictwo(boolean b) { this.brakPelnomocnictwo = b; }
    public boolean isBrakUzasadnienie() { return brakUzasadnienie; }
    public void setBrakUzasadnienie(boolean b) { this.brakUzasadnienie = b; }
    public String getUwaga() { return uwaga; }
    public void setUwaga(String uwaga) { this.uwaga = uwaga; }
}
