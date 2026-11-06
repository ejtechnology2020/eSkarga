package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
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
    private ViewData data;
    @Inject
    private pl.ejtechnology.tk.eSkarga.security.OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.NoteService notes;

    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.ChecklistService checklist;
    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService cases;

    /** Z parametru ?sygnatura=; w demo bez parametru Ts 312/26 (load). Poza demo bez parametru – powrót do repertorium. */
    private String sygnatura;
    private boolean komunikatDodany;
    /** Uwagi wpisywane przy ocenie wymogów (kod wymogu → treść) – zapisywane razem z oceną. */
    private final java.util.Map<String, String> uwagi = new java.util.HashMap<>();
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

    /** {@code f:viewAction}: z bazą bez sprawy (brak parametru albo brak dostępu) – powrót do repertorium z komunikatem; w demo domyślnie Ts 312/26. */
    public String load() {
        if (settings.isDemo() && (sygnatura == null || sygnatura.isBlank())) {
            sygnatura = "Ts 312/26";
        }
        sprawa = data.caseBy(sygnatura);
        if (sprawa == null && !settings.isDemo()) {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null && !komunikatDodany) {   // init() i f:viewAction wołają load() – komunikat tylko raz
                komunikatDodany = true;
                fc.getExternalContext().getFlash().setKeepMessages(true);
                fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, sygnatura == null || sygnatura.isBlank() ? "Wybierz sprawę z repertorium."
                        : "Brak sprawy " + sygnatura + " albo brak dostępu do jej akt.", null));
            }
            return "/sprawy/repertorium.xhtml?faces-redirect=true";
        }
        if (sprawa == null) {
            sprawa = data.caseBy("Ts 312/26");
            sygnatura = "Ts 312/26";
        }
        spis = data.recordPages(sygnatura);
        dokument = spis.isEmpty() ? null : spis.get(0);
        if (widok == null) {
            AppRole r = currentUser.getRole();
            widok = r == AppRole.TK_REFERENT || r == AppRole.TK_KIEROWNIK ? "referent" : "asystent";
        }
        if ("zakonczona".equals(widok)) {
            sprawa = data.caseBy("Ts 287/26");
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
        return null;
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

    /** Zaznaczenia zespołu sędziego (T-035) jako JSON dla nakładki widżetu PDF; poza zespołem – pusta lista (FR-24). */
    public String highlightsJson(RecordPage doc) {
        if (currentUser.isDemo() || doc == null || !doc.id().matches("\\d+")) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (var h : notes.highlights(Long.parseLong(doc.id()), currentUser.getLogin(), currentUser.getRoles())) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append("{\"page\":").append(h.getPageNo()).append(",\"comment\":\"").append(h.getCommentText() == null ? "" : h.getCommentText().replace("\"", "'")).append("\"");
            String g = h.getGeometryJson();
            if (g != null && g.startsWith("{") && g.endsWith("}") && g.length() > 2) {
                sb.append(',').append(g, 1, g.length() - 1);
            }
            sb.append('}');
        }
        return sb.append(']').toString();
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
    public List<TimelineEntry> getCzynnosci() { return data.timeline(sygnatura); }
    /** Projekty pism do podpisu (S-16) – odnośnik „Podpisz” dla sędziego i Prezesa. */
    public List<pl.ejtechnology.tk.eSkarga.web.model.DraftRow> getProjekty() { return data.drafts(sygnatura); }
    public List<ProcessStep> getPrzebieg() { return data.processSteps(sygnatura); }
    public List<ChecklistItem> getLista() { return data.checklist(sygnatura); }

    public java.util.Map<String, String> getUwagi() { return uwagi; }

    /** UC-06: ocena wymogu (SPELNIONY / BRAK / DO_SPRAWDZENIA) z uwagą – zapis w usłudze listy kontrolnej, audyt. */
    public void ocen(String kod, String stan) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (settings.isDemo()) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Tryb demo: ocena nie jest zapisywana.", null));
            return;
        }
        try {
            var cf = cases.byDocket(pl.ejtechnology.tk.eSkarga.domain.DocketNumber.parse(sygnatura)).orElseThrow(() -> new IllegalArgumentException("Brak sprawy " + sygnatura));
            checklist.record(cf.getId(), kod, pl.ejtechnology.tk.eSkarga.domain.ChecklistState.valueOf(stan), uwagi.get(kod), null, currentUser.getLogin(), currentUser.getRoles());
        } catch (RuntimeException e) {
            fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać oceny wymogu.", e.getMessage()));
        }
    }
    public List<DeadlineView> getTerminy() { return data.deadlines(sygnatura); }
    public long getBraki() { return getLista().stream().filter(i -> i.stan() == ChecklistItem.State.BRAK).count(); }
    public int getJawne() { return (int) spis.stream().filter(p -> !p.isNiejawny()).count(); }
}
