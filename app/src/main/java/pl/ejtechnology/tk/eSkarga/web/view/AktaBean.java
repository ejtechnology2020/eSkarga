package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.ChecklistItem;
import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;
import pl.ejtechnology.tk.eSkarga.web.model.ProcessStep;
import pl.ejtechnology.tk.eSkarga.web.model.RecordPage;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/**
 * S-14 Podgląd akt sprawy (brief podglądu akt, FR-23, FR-24). Stały nagłówek sprawy, spis akt (tomy, karty,
 * chronologia; zakładki Akta jawne / Notatki wewnętrzne), podgląd dokumentu, panel boczny (Czynności, Terminy,
 * Lista kontrolna, Metadane). Układ jest ten sam dla wszystkich ról i statusów – zmieniają się treść i akcje (§5).
 *
 * <p>Warianty: {@code widok=asystent|referent|anonim|zakonczona}; dokument niejawny tylko jako plansza z metadanymi (BR-09).
 */
@Named("akta")
@ViewScoped
public class AktaBean implements Serializable {

    @Inject
    private DemoDataset demo;
    @Inject
    private CurrentUser currentUser;

    private String sygnatura = "Ts 312/26";
    private String widok;
    private CaseSummary sprawa;
    private List<RecordPage> spis;
    private RecordPage dokument;
    private RecordPage drugiDokument;
    private boolean split;
    private int panelTab;
    private boolean notatki;

    @PostConstruct
    void init() {
        load();
    }

    public void load() {
        sprawa = demo.caseBy(sygnatura);
        if (sprawa == null) {
            sprawa = demo.caseBy("Ts 312/26");
            sygnatura = "Ts 312/26";
        }
        spis = demo.recordPages(sygnatura);
        dokument = spis.get(0);
        if (widok == null) {
            AppRole r = currentUser.getRole();
            widok = r == AppRole.TK_REFERENT || r == AppRole.TK_KIEROWNIK ? "referent" : "asystent";
        }
        if ("zakonczona".equals(widok)) {
            sprawa = demo.caseBy("Ts 287/26");
            sygnatura = sprawa.sygnatura().format();
        }
        if ("lista".equals(widok)) {
            panelTab = 2;
            widok = "asystent";
        }
        if ("dzielony".equals(widok)) {
            split = true;
            drugiDokument = spis.get(1);
            widok = "asystent";
        }
    }

    public void open(RecordPage p) {
        if (!p.isNiejawny() || true) {
            dokument = p;
        }
    }

    public void openSecond(RecordPage p) {
        drugiDokument = p;
        split = true;
    }

    public void toggleSplit() {
        split = !split;
        if (split && drugiDokument == null) {
            drugiDokument = spis.size() > 1 ? spis.get(1) : dokument;
        }
    }

    public boolean isZespolSedziego() {
        return "asystent".equals(widok);
    }

    public boolean isReferent() {
        return "referent".equals(widok);
    }

    public boolean isAnonim() {
        return "anonim".equals(widok);
    }

    public boolean isReadOnly() {
        return sprawa.status().isReadOnly() || isAnonim();
    }

    /** W widoku zanonimizowanym dane osobowe to inicjały albo […] (BR-09). */
    public String anon(String name) {
        if (!isAnonim() || name == null || name.isBlank() || name.startsWith("—")) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : name.replace("adw. ", "").replace("r.pr. ", "").split("\\s+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    public String getAkcjaGlowna() {
        return switch (sprawa.status()) {
            case OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO -> "Przedstaw Prezesowi";
            case WE_WSTEPNEJ_KONTROLI -> "Lista kontrolna";
            case WEZWANIE_DO_USUNIECIA_BRAKOW -> isReferent() ? "Dodaj pismo uzupełniające" : "Dodaj pismo";
            case ODMOWA_TERMIN_NA_ZAZALENIE -> "Zarejestruj zażalenie";
            case ZAZALENIE_W_ROZPOZNANIU -> "Podgląd składu";
            case ZAKONCZONA_NADANO_BIEG -> "Przejdź do SK 41/26";
            case ZAKONCZONA_ODMOWA_PRAWOMOCNA -> "Przekaż do archiwum";
            case ZAWIESZONA -> "Podejmij";
            default -> "Nowa czynność";
        };
    }

    public boolean isBiegnacyTermin() {
        return sprawa.termin() != null && sprawa.termin().end() != null && sprawa.status() == CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW;
    }

    public List<RecordPage> getSpis() { return spis; }
    public RecordPage getDokument() { return dokument; }
    public RecordPage getDrugiDokument() { return drugiDokument; }
    public boolean isSplit() { return split; }
    public int getPanelTab() { return panelTab; }
    public void setPanelTab(int panelTab) { this.panelTab = panelTab; }
    public boolean isNotatki() { return notatki; }
    public void setNotatki(boolean notatki) { this.notatki = notatki; }
    public CaseSummary getSprawa() { return sprawa; }
    public String getSygnatura() { return sygnatura; }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }
    public String getWidok() { return widok; }
    public void setWidok(String widok) { this.widok = widok; }
    public List<TimelineEntry> getCzynnosci() { return demo.timeline(sygnatura); }
    public List<ProcessStep> getPrzebieg() { return demo.processSteps(sygnatura); }
    public List<ChecklistItem> getLista() { return demo.checklist(sygnatura); }
    public List<DeadlineView> getTerminy() { return demo.deadlines(sygnatura); }
    public long getBraki() { return getLista().stream().filter(i -> i.stan() == ChecklistItem.State.BRAK).count(); }
    public int getJawne() { return (int) spis.stream().filter(p -> !p.isNiejawny()).count(); }
}
