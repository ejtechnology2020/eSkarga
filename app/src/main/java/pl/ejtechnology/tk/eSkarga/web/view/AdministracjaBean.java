package pl.ejtechnology.tk.eSkarga.web.view;

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
    private DemoDataset demo;

    private int tab = 2;
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

    public List<UserRow> getUzytkownicy() { return demo.users(); }
    public List<DelegationRow> getUpowaznienia() { return demo.delegations(); }
    public List<TemplateRow> getSzablony() { return demo.templates(); }
    public List<DictionaryRow> getWzorce() { return demo.reviewStandards(); }
    public List<CalendarMonth> getKalendarz() { return demo.calendar(rok); }
    public List<AuditRow> getZdarzenia() { return demo.audit(); }
    public AuditRow getWybraneZdarzenie() { return demo.audit().get(0); }
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
