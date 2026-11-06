package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.LetterKind;
import pl.ejtechnology.tk.eSkarga.domain.service.LetterService;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
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
    private ViewData data;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private OidcSettings settings;
    @Inject
    private LetterService letters;
    @Inject
    private CaseFileService cases;
    private String sygnaturaParam;
    private String uzasadnienieTekst;

    private String stan;
    private String rodzaj = "odmowa";
    /** Ostatnio sporządzony projekt (poza demo): dokument i rodzaj do odnośnika „Zaakceptuj i podpisz” → S-16. */
    private Long projektDokumentId;
    private String projektRodzaj;
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
        if (!settings.isDemo()) {
            try {
                var cf = cases.byDocket(DocketNumber.parse(getSygnatura())).orElseThrow(() -> new IllegalArgumentException("Brak sprawy " + getSygnatura()));
                LetterKind kind = switch (rodzaj == null ? "" : rodzaj) {
                    case "braki" -> LetterKind.ZARZ_BRAKI;
                    case "nadanie" -> LetterKind.POST_NADANIE_BIEGU;
                    case "zazalenie" -> LetterKind.POST_ZAZALENIE_ROZSTRZYGNIECIE;
                    case "bez-rozpoznania" -> LetterKind.POST_ZAZALENIE_BEZ_ROZPOZNANIA;
                    default -> LetterKind.POST_ODMOWA;
                };
                java.util.Map<String, Object> extra = new java.util.LinkedHashMap<>();
                String digits = podstawa.replaceAll("\\D", "");
                extra.put("podstawaOdmowy", Integer.parseInt(digits.isEmpty() ? "3" : digits));
                extra.put("uzasadnienie", uzasadnienieTekst == null || uzasadnienieTekst.isBlank() ? List.of(getUzasadnienie()) : List.of(uzasadnienieTekst.split("\\n\\n")));
                extra.put("braki", getBraki().stream().filter(b -> b.wstawDoPisma() || b.stan() == ChecklistItem.State.BRAK).map(b -> b.wymog() + " (" + b.podstawa() + ")").toList());
                var act = letters.draft(cf.getId(), kind, extra, currentUser.getDisplayName(), currentUser.getLogin());
                projektDokumentId = act.getDocument() == null ? null : act.getDocument().getId();
                projektRodzaj = kind.name();
            } catch (RuntimeException e) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się sporządzić projektu.", e.getMessage()));
                return;
            }
        }
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Projekt przesłany sędziemu.", settings.isDemo() ? "Wersja v3 z 3 · 03.11.2026 09:02." : "Projekt w aktach, czynność w stanie PROJEKT."));
    }
    public Long getProjektDokumentId() { return projektDokumentId; }
    public String getProjektRodzaj() { return projektRodzaj; }
    public String getSygnaturaParam() { return sygnaturaParam; }
    public void setSygnaturaParam(String s) { this.sygnaturaParam = s; }
    public String getUzasadnienieTekst() { return uzasadnienieTekst; }
    public void setUzasadnienieTekst(String s) { this.uzasadnienieTekst = s; }

    public boolean isSklad() { return "sklad".equals(stan); }
    public boolean isSedzia() { return "sedzia".equals(stan) || isSklad(); }
    public boolean isLock() { return "lock".equals(stan); }
    public boolean isAsystent() { return !isSedzia() && !isLock(); }
    public boolean isReadOnly() { return isLock() || isSedzia(); }

    public String getSygnatura() { return sygnaturaParam != null && !sygnaturaParam.isBlank() ? sygnaturaParam : isSklad() ? "Ts 276/26" : "Ts 312/26"; }
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

    public List<ChecklistItem> getBraki() { return data.checklist(getSygnatura()); }
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
