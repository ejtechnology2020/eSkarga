package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import pl.ejtechnology.tk.eSkarga.documents.DocsGateway;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;
import pl.ejtechnology.tk.eSkarga.domain.service.HolidayService;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.AuditRow;
import pl.ejtechnology.tk.eSkarga.web.model.CalendarMonth;
import pl.ejtechnology.tk.eSkarga.web.model.DelegationRow;
import pl.ejtechnology.tk.eSkarga.web.model.DictionaryRow;
import pl.ejtechnology.tk.eSkarga.web.model.TemplateRow;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;
import pl.ejtechnology.tk.eSkarga.web.model.UserRow;

/** Ekrany administracyjne: użytkownicy (S-22), upoważnienia (S-23), słowniki/szablony/dni wolne (S-24), dziennik zdarzeń (S-25). */
@Named("administracja")
@ViewScoped
public class AdministracjaBean implements Serializable {

    @Inject
    private ViewData data;

    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private DelegationService delegations;
    @Inject
    private HolidayService holidays;
    @Inject
    private AuditService audit;
    @Inject
    private TemplateService templates;
    @Inject
    private DocsGateway docs;
    private java.time.LocalDate od;
    private java.time.LocalDate doDnia;
    private String akt;
    private java.time.LocalDate dzienWolny;
    private String opisDnia;
    private String kodSzablonu = "ZARZ_BRAKI";
    private String nazwaSzablonu;
    private String zrodloSzablonu;
    private List<String> bledySzablonu = List.of();
    private int tab = 2;
    /** FR-36: nowe upoważnienie z aktem podstawowym. */
    public void addDelegation() {
        if (settings.isDemo()) {
            return;
        }
        try {
            delegations.add(osoba, "prezes".equals(zakres) ? CaseAccessPolicy.FUNCTION_PRESIDENT : zakres, od, doDnia, akt, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Upoważnienie dodane.", osoba));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się dodać upoważnienia.", e.getMessage()));
        }
    }
    public void revokeDelegation(String id) {
        if (!settings.isDemo()) {
            delegations.revoke(Long.parseLong(id), currentUser.getLogin());
        }
    }
    /** NFR-02: weryfikacja łańcucha skrótów dziennika zdarzeń (S-25). */
    public void verifyChain() {
        if (settings.isDemo()) {
            return;
        }
        var r = audit.verifyChain();
        lancuch = r.consistent() ? "spojny" : "alarm";
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(r.consistent() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                r.consistent() ? "Łańcuch spójny." : "Naruszenie łańcucha!", r.consistent() ? "Sprawdzono " + r.checked() + " wpisów."
                        : "Pierwszy niezgodny wpis: " + r.firstBroken().map(e -> String.valueOf(e.getId())).orElse("?")));
    }
    public void addHoliday() {
        if (!settings.isDemo() && dzienWolny != null) {
            holidays.add(dzienWolny, opisDnia == null ? "dzień wolny w Trybunale" : opisDnia, currentUser.getLogin());
        }
    }
    /** S-24 (T-026a): walidacja szablonu w usłudze dokumentów (kompilacja, render na Ts 312/26, veraPDF) przed zapisem i aktywacją. */
    public void validateTemplate() {
        if (settings.isDemo()) {
            bledySzablonu = zrodloSzablonu != null && zrodloSzablonu.contains("${") && !zrodloSzablonu.contains("}") ? List.of("niezamknięte wyrażenie ${") : List.of();
            return;
        }
        var r = docs.validateTemplate(kodSzablonu, zrodloSzablonu);
        bledySzablonu = r.errors();
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(r.ok() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_WARN,
                r.ok() ? "Szablon poprawny." : "Szablon z błędami: " + r.errors().size(), null));
    }
    public void saveTemplateDraft() {
        if (settings.isDemo()) {
            return;
        }
        try {
            var t = templates.saveDraft(kodSzablonu, nazwaSzablonu == null ? kodSzablonu : nazwaSzablonu, zrodloSzablonu, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Zapisano projekt szablonu.", t.getCode() + " v" + t.getVersion()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać szablonu.", e.getMessage()));
        }
    }
    public void activateTemplate(long templateId) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var v = templates.activate(templateId, currentUser.getLogin());
            bledySzablonu = v.errors();
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(v.ok() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                    v.ok() ? "Szablon aktywowany." : "Aktywacja odrzucona przez walidację.", null));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się aktywować szablonu.", e.getMessage()));
        }
    }
    public void seedTemplates() {
        if (!settings.isDemo()) {
            int n = templates.seedDefaults(docs.defaultTemplates(), currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Szablony domyślne: " + n, null));
        }
    }
    public java.time.LocalDate getOd() { return od; }
    public void setOd(java.time.LocalDate od) { this.od = od; }
    public java.time.LocalDate getDoDnia() { return doDnia; }
    public void setDoDnia(java.time.LocalDate d) { this.doDnia = d; }
    public String getAkt() { return akt; }
    public void setAkt(String akt) { this.akt = akt; }
    public java.time.LocalDate getDzienWolny() { return dzienWolny; }
    public void setDzienWolny(java.time.LocalDate d) { this.dzienWolny = d; }
    public String getOpisDnia() { return opisDnia; }
    public void setOpisDnia(String o) { this.opisDnia = o; }
    public String getKodSzablonu() { return kodSzablonu; }
    public void setKodSzablonu(String k) { this.kodSzablonu = k; }
    public String getNazwaSzablonu() { return nazwaSzablonu; }
    public void setNazwaSzablonu(String n) { this.nazwaSzablonu = n; }
    public String getZrodloSzablonu() { return zrodloSzablonu; }
    public void setZrodloSzablonu(String z) { this.zrodloSzablonu = z; }
    public List<String> getBledySzablonu() { return bledySzablonu; }
    private String zakladka;
    private String lancuch = "spojny";
    private int rok = 2026;
    private String osoba;
    private String zakres = "prezes";
    private String uzytkownikFiltr;
    private String sprawaFiltr;

    public void applyState() {
        if (zakladka != null) {
            tab = switch (zakladka) { case "slowniki" -> 0; case "szablony" -> 1; default -> 2; };
        }
    }

    public List<UserRow> getUzytkownicy() { return data.users(); }
    public List<DelegationRow> getUpowaznienia() { return data.delegations(); }
    public List<TemplateRow> getSzablony() { return data.templates(); }
    public List<DictionaryRow> getWzorce() { return data.reviewStandards(); }
    public List<CalendarMonth> getKalendarz() { return data.calendar(rok); }
    public List<AuditRow> getZdarzenia() { return data.audit(); }
    public AuditRow getWybraneZdarzenie() { var a = data.audit(); return a.isEmpty() ? null : a.get(0); }
    public boolean isAlarm() { return "alarm".equals(lancuch); }

    public List<TimelineEntry> getHistoriaUpowaznienia() {
        return List.of(new TimelineEntry(DemoDataset.dt(2026, 10, 30, 12, 10), "Utworzono", "· 30.10–31.12.2026 · Anna Admin", null, true, true),
                new TimelineEntry(DemoDataset.dt(2026, 10, 30, 12, 14), "Dołączono akt", "zarządzenie nr 12/2026 · Anna Admin", null, false, true));
    }

    public int getTab() { return tab; }
    public void setTab(int tab) { this.tab = tab; }
    public String getZakladka() { return zakladka; }
    public void setZakladka(String zakladka) { this.zakladka = zakladka; }
    public String getLancuch() { return lancuch; }
    public void setLancuch(String lancuch) { this.lancuch = lancuch; }
    public int getRok() { return rok; }
    public void setRok(int rok) { this.rok = rok; }
    public void prevYear() { rok--; }
    public void nextYear() { rok++; }
    public String getOsoba() { return osoba; }
    public void setOsoba(String osoba) { this.osoba = osoba; }
    public String getZakres() { return zakres; }
    public void setZakres(String zakres) { this.zakres = zakres; }
    public String getUzytkownikFiltr() { return uzytkownikFiltr; }
    public void setUzytkownikFiltr(String u) { this.uzytkownikFiltr = u; }
    public String getSprawaFiltr() { return sprawaFiltr; }
    public void setSprawaFiltr(String s) { this.sprawaFiltr = s; }
}
