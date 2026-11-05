package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.domain.model.Profession;
import pl.ejtechnology.tk.eSkarga.domain.model.PowerOfAttorneyKind;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineCalculator;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineComputation;
import pl.ejtechnology.tk.eSkarga.domain.deadline.PolishHolidays;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.Effect;

/**
 * S-07 Założenie sprawy Ts/Tw (UC-03): formularz w 3 krokach (p:steps) – Strony, Przedmiot, Podsumowanie
 * z pomocniczym wyliczeniem terminu z art. 77. „Zatwierdź wpis” nadaje sygnaturę w oknie potwierdzenia,
 * które pokazuje kolejną sygnaturę (podgląd licznika bez rezerwacji numeru – BR-01).
 */
@Named("zalozenie")
@ViewScoped
public class ZalozenieBean implements Serializable {

    @Inject
    private ViewData data;
    @Inject
    private Fmt fmt;
    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private IncomingRegister incoming;
    @Inject
    private CaseFileService cases;
    @Inject
    private DocketNumberService docketNumbers;
    @Inject
    private AppClock clock;
    private String pismo;
    private String pelnomocnik = "adw. Anna Testowa";
    private String adresEDoreczen;
    private boolean pilna;
    private boolean anonimizacja;

    private int krok = 2; // indeks 0..2
    private String stan;
    private String repertorium = "TS";
    private String skarzacy = "Stefan Przykładny";
    private String adres = "ul. Przykładowa 1, 00-001 Przykładowo";
    private boolean dzialaSam = true;
    private String przepisy = "art. 394¹ § 1 k.p.c.";
    private String wzorce = "art. 45 ust. 1, art. 78 Konstytucji";
    private String organ = "postanowienie Sądu Apelacyjnego w Przykładowie";
    private String sygnOrzeczenia = "I ACz 77/26";
    private LocalDate doreczenie = LocalDate.of(2026, 8, 5);
    private LocalDate wniesienie = LocalDate.of(2026, 10, 29);
    /** Opis pisma źródłowego (DW, wpływ, wniesienie) i jego nadawca – z bazy; w demo wartości z makiety. */
    private String pismoOpis = "DW 2026/04809 · wpływ 02.11.2026";
    private String pismoNadawca = "Stefan Przykładny · 6 str.";
    private int pismoSkany;
    private boolean brakPisma;
    private boolean duplikat = true;
    private boolean dialog;

    /** Parametr {@code stan} odtwarza warianty z makiety: krok1, krok2, blad, krok3, art77, okno. */
    public void applyState() {
        if (!settings.isDemo()) {
            loadLetter();
            return;
        }
        switch (stan == null ? "" : stan) {
            case "krok1" -> krok = 0;
            case "krok2" -> krok = 1;
            case "blad" -> {
                krok = 1; przepisy = null;
                FacesContext fc = FacesContext.getCurrentInstance();
                fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Popraw 2 pola w kroku 2.", null));
                fc.addMessage("p-prz", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dodaj co najmniej jeden zaskarżony przepis.", null));
                fc.addMessage("p-dor", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Podaj datę w formacie DD.MM.RRRR.", null));
            }
            case "art77" -> { krok = 2; doreczenie = LocalDate.of(2026, 7, 20); }
            case "okno" -> { krok = 2; dialog = true; }
            default -> krok = 2;
        }
    }

    /** Tryb z bazą: formularz zaczyna się od pisma DW z kolejki (UC-02 → UC-03); bez parametru {@code pismo} nie da się założyć sprawy. */
    private void loadLetter() {
        krok = 0;
        duplikat = false;
        skarzacy = null; adres = null; pelnomocnik = null; dzialaSam = true;
        przepisy = null; wzorce = null; organ = null; sygnOrzeczenia = null; doreczenie = null;
        var letter = pismo == null || pismo.isBlank() ? java.util.Optional.<pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter>empty() : incoming.byRegisterNo(pismo.trim());
        if (letter.isEmpty()) {
            brakPisma = true;
            pismoOpis = pismo == null || pismo.isBlank() ? "brak pisma" : "nie znaleziono pisma " + pismo;
            pismoNadawca = "";
            return;
        }
        var l = letter.get();
        pismo = l.getRegisterNo();
        skarzacy = l.getSender();
        wniesienie = l.getFiledOn() != null ? l.getFiledOn() : l.getReceivedAt().atZone(AppClock.WARSAW).toLocalDate();
        pismoOpis = l.getRegisterNo() + " · wpływ " + fmt.date(l.getReceivedAt().atZone(AppClock.WARSAW).toLocalDate()) + " · wniesienie " + fmt.date(wniesienie);
        pismoSkany = incoming.scans(l.getId()).size();
        pismoNadawca = l.getSender() + (pismoSkany > 0 ? " · " + pismoSkany + " plik(ów)" : " · bez skanu");
        if (l.getCaseFile() != null) {
            brakPisma = true;
            pismoOpis = l.getRegisterNo() + " jest już w sprawie " + l.getCaseFile().getDocket().format();
        }
    }

    public void next() {
        if (krok == 1 && (przepisy == null || przepisy.isBlank())) {
            FacesContext fc = FacesContext.getCurrentInstance();
            fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Popraw pola w kroku 2.", null));
            fc.addMessage("p-prz", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dodaj co najmniej jeden zaskarżony przepis.", null));
            return;
        }
        if (krok < 2) {
            krok++;
        }
    }

    public void back() {
        if (krok > 0) {
            krok--;
        }
    }

    public void openDialog() {
        dialog = true;
    }

    public void closeDialog() {
        dialog = false;
    }

    /** Zatwierdzenie w jednej transakcji: sygnatura + wpis + start procesu (D1, T-013). */
    /** UC-03 (D1): sygnatura, wpis, strony, orzeczenie, karty akt i start procesu w jednej transakcji. */
    public String confirm() {
        dialog = false;
        DocketNumber docket = getNastepnaSygnatura();
        if (!settings.isDemo()) {
            try {
                long letterId = incoming.byRegisterNo(pismo == null ? "" : pismo).orElseThrow(() -> new IllegalArgumentException("Wybierz pismo z kolejki (parametr pismo)")).getId();
                List<CaseFileService.PartyData> parties = new java.util.ArrayList<>();
                parties.add(new CaseFileService.PartyData(repertorium.equals("TS") ? PartyRole.SKARZACY : PartyRole.WNIOSKODAWCA, skarzacy, adres, adresEDoreczen, null, null, null, null));
                if (!dzialaSam && pelnomocnik != null && !pelnomocnik.isBlank()) {
                    parties.add(new CaseFileService.PartyData(PartyRole.PELNOMOCNIK, pelnomocnik, null, null, PowerOfAttorneyKind.SZCZEGOLNE,
                            pelnomocnik.startsWith("r.pr.") ? Profession.RADCA_PRAWNY : Profession.ADWOKAT, null, wniesienie));
                }
                var cf = cases.createCase(new CaseFileService.CreateCaseCommand(DocketSymbol.valueOf(repertorium), letterId, parties, przepisy, null, wzorce,
                        organ, sygnOrzeczenia, null, doreczenie, false, pilna, anonimizacja, 6, currentUser.getLogin()));
                docket = cf.getDocket();
            } catch (RuntimeException e) {
                java.util.logging.Logger.getLogger(ZalozenieBean.class.getName()).log(java.util.logging.Level.WARNING,
                        "Założenie sprawy z pisma " + pismo + " nie powiodło się: " + e, e);   // bez danych osobowych: numer dziennika i wyjątek
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się założyć sprawy.",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                return null;
            }
        }
        return "/akta/podglad.xhtml?faces-redirect=true&sygnatura=" + docket.format().replace(" ", "%20");
    }

    /** Kolejna sygnatura do okna potwierdzenia (podgląd licznika). */
    public DocketNumber getNastepnaSygnatura() {
        if (!settings.isDemo()) {
            try {
                return docketNumbers.peekNextNumber(DocketSymbol.valueOf(repertorium), clock.today());
            } catch (RuntimeException e) {
                return new DocketNumber(DocketSymbol.valueOf(repertorium), 0, clock.today().getYear());
            }
        }
        return new DocketNumber(DocketSymbol.valueOf(repertorium), repertorium.equals("TS") ? 319 : 15, 2026);
    }
    public String getPismo() { return pismo; }
    public void setPismo(String pismo) { this.pismo = pismo; }
    public String getPelnomocnik() { return pelnomocnik; }
    public void setPelnomocnik(String pelnomocnik) { this.pelnomocnik = pelnomocnik; }
    public String getAdresEDoreczen() { return adresEDoreczen; }
    public void setAdresEDoreczen(String a) { this.adresEDoreczen = a; }
    public boolean isPilna() { return pilna; }
    public void setPilna(boolean pilna) { this.pilna = pilna; }
    public boolean isAnonimizacja() { return anonimizacja; }
    public void setAnonimizacja(boolean anonimizacja) { this.anonimizacja = anonimizacja; }

    /** Termin z art. 77 – wyliczenie pomocnicze, ocenia sędzia (BR-03, D6). */
    /** Null, gdy nie podano daty doręczenia orzeczenia – ekran nie pokazuje wtedy wyliczenia. */
    public DeadlineComputation getArt77() {
        if (doreczenie == null) {
            return null;
        }
        return new DeadlineCalculator(new PolishHolidays(data.extraDaysOff(doreczenie.getYear()))).threeMonthsFromRulingDelivery(doreczenie, List.of());
    }

    public boolean isArt77Przekroczony() {
        DeadlineComputation a = getArt77();
        return a != null && wniesienie != null && wniesienie.isAfter(a.end());
    }

    public long getArt77DniPo() {
        DeadlineComputation a = getArt77();
        return a == null || wniesienie == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(a.end(), wniesienie);
    }

    public int getArt77Progress() {
        DeadlineComputation a = getArt77();
        return a == null || wniesienie == null ? 0 : new DeadlineCalculator(new PolishHolidays()).progressPercent(a.start(), a.end(), wniesienie);
    }

    public List<Effect> getSkutki() {
        String rep = repertorium.equals("TS") ? "Ts" : "Tw";
        return List.of(
                Effect.next("Wpis trafi do repertorium " + rep + " " + clock.today().getYear() + ". Sygnatury nie można zmienić — tylko skorygować wpis z akceptacją kierownika."),
                Effect.next("Pismo " + (pismo == null ? "" : pismo) + " zostanie pierwszymi kartami akt."),
                Effect.next("Sprawa czeka na wyznaczenie sędziego przez Prezesa TK."));
    }

    public String getPismoOpis() { return pismoOpis; }
    public String getPismoNadawca() { return pismoNadawca; }
    public int getPismoSkany() { return pismoSkany; }
    public boolean isBrakPisma() { return brakPisma; }
    /** Podtytuł ekranu: rodzaj pisma i skarżący. */
    public String getPodtytul() {
        return (repertorium.equals("TS") ? "Skarga konstytucyjna" : "Wniosek") + (skarzacy == null || skarzacy.isBlank() ? "" : " · " + skarzacy);
    }

    public String stepClass(int i) {
        return i < krok ? "done" : "";
    }

    public int getKrok() { return krok; }
    public void setKrok(int krok) { this.krok = krok; }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getRepertorium() { return repertorium; }
    public void setRepertorium(String repertorium) { this.repertorium = repertorium; }
    public String getSkarzacy() { return skarzacy; }
    public void setSkarzacy(String skarzacy) { this.skarzacy = skarzacy; }
    public String getAdres() { return adres; }
    public void setAdres(String adres) { this.adres = adres; }
    public boolean isDzialaSam() { return dzialaSam; }
    public void setDzialaSam(boolean dzialaSam) { this.dzialaSam = dzialaSam; }
    public String getPrzepisy() { return przepisy; }
    public void setPrzepisy(String przepisy) { this.przepisy = przepisy; }
    public String getWzorce() { return wzorce; }
    public void setWzorce(String wzorce) { this.wzorce = wzorce; }
    public String getOrgan() { return organ; }
    public void setOrgan(String organ) { this.organ = organ; }
    public String getSygnOrzeczenia() { return sygnOrzeczenia; }
    public void setSygnOrzeczenia(String sygnOrzeczenia) { this.sygnOrzeczenia = sygnOrzeczenia; }
    public LocalDate getDoreczenie() { return doreczenie; }
    public void setDoreczenie(LocalDate doreczenie) { this.doreczenie = doreczenie; }
    public LocalDate getWniesienie() { return wniesienie; }
    public boolean isDuplikat() { return duplikat; }
    public void setDuplikat(boolean duplikat) { this.duplikat = duplikat; }
    public boolean isDialog() { return dialog; }
    public String getFmtDoreczenie() { return fmt.date(doreczenie); }
}
