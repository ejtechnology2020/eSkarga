package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.model.Appeal;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Judge;
import pl.ejtechnology.tk.eSkarga.domain.service.AppealService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.JudgeDictionaryService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;

/**
 * S-18 Skierowanie zażalenia do składu (UC-11, BR-07, ADR 0027): dane zażalenia z pomocniczą oceną terminowości, wybór 3 sędziów
 * ze słownika sędziów (aktywnych, w kadencji) z wykluczeniem sędziego, który wydał odmowę, przewodniczący i sprawozdawca spośród
 * wybranych, termin posiedzenia niejawnego. Zażalenie po terminie – postanowienie o pozostawieniu bez rozpoznania.
 * W trybie demo ekran pokazuje dane przykładowe ({@code ?stan=}).
 */
@Named("sklad")
@ViewScoped
public class SkladBean implements Serializable {

    /** Kandydat do składu: login, nazwa ze słownika, obciążenie, wykluczenie (BR-07) z powodem. */
    public record Kandydat(String login, String nazwa, String funkcja, boolean wykluczony, String powod) implements Serializable {
        public String etykieta() {
            return nazwa + (wykluczony ? " — niedostępny: " + powod : "");
        }
    }

    @Inject
    private ViewData data;
    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private AppealService appeals;
    @Inject
    private CaseFileService cases;
    @Inject
    private JudgeDictionaryService judgeDictionary;
    @Inject
    private AppClock clock;

    private String stan = "w-terminie";
    private String sygnaturaParam;
    private String przewodniczacy = "Ewa Wzorcowa";
    private String sprawozdawca = "Piotr Fikcyjny";
    private LocalDate posiedzenie = LocalDate.of(2026, 11, 19);

    // tryb rzeczywisty (OIDC)
    private CaseSummary sprawa;
    private Long appealId;
    private Boolean wTerminie;
    private LocalDate wniesiono;
    private String sedziaOdmowy;
    private String skladZapisany;
    private List<Kandydat> kandydaci = new ArrayList<>();
    private List<String> wybrani = new ArrayList<>();
    private String przewodniczacyLogin;
    private String sprawozdawcaLogin;

    @PostConstruct
    void init() {
        load();
    }

    /** {@code f:viewAction}: poza demo ładuje sprawę i zażalenie czekające na skład; bez sprawy – komunikat. */
    public void load() {
        if (settings.isDemo() || sygnaturaParam == null || sygnaturaParam.isBlank()) {
            return;
        }
        sprawa = data.caseBy(sygnaturaParam);
        if (sprawa == null) {
            return;
        }
        Optional<CaseFile> cfo = cases.byDocket(DocketNumber.parse(sygnaturaParam));
        if (cfo.isEmpty()) {
            return;
        }
        CaseFile cf = cfo.get();
        Optional<Appeal> ap = appeals.pendingFor(cf);
        String refusing = cf.current().getJudgeUserId();
        sedziaOdmowy = refusing == null ? "—" : judgeDictionary.displayName(refusing);
        if (ap.isPresent()) {
            appealId = ap.get().getId();
            wTerminie = ap.get().getInTime();
            wniesiono = ap.get().getFiledOn();
            skladZapisany = ap.get().getPanelUserIds();
            if (ap.get().getSessionOn() != null) {
                posiedzenie = ap.get().getSessionOn();
            } else {
                posiedzenie = clock.today().plusDays(14);
            }
        }
        kandydaci = new ArrayList<>();
        for (Judge j : judgeDictionary.eligibleOn(clock.today())) {
            boolean excluded = refusing != null && refusing.equals(j.getUserId());
            kandydaci.add(new Kandydat(j.getUserId(), j.titledName(), j.getFunction().label(), excluded, excluded ? "wydał zaskarżone postanowienie o odmowie (BR-07)" : null));
        }
        // Prezes / Wiceprezes jako domyślny przewodniczący, gdy jest w słowniku i nie jest wykluczony
        przewodniczacyLogin = kandydaci.stream().filter(k -> !k.wykluczony() && !"sędzia TK".equals(k.funkcja())).map(Kandydat::login).findFirst().orElse(null);
    }

    /** UC-11: skierowanie do składu – 3 różnych sędziów ze słownika, przewodniczący i sprawozdawca spośród wybranych; wymaga upoważnienia (BR-10). */
    public String skieruj() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (appealId == null) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Brak zażalenia do skierowania w tej sprawie.", null));
            return null;
        }
        if (wybrani == null || wybrani.size() != 3) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Wybierz dokładnie trzech sędziów.", null));
            return null;
        }
        if (przewodniczacyLogin == null || !wybrani.contains(przewodniczacyLogin)) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Przewodniczący musi być jednym z wybranych sędziów.", null));
            return null;
        }
        if (sprawozdawcaLogin == null || !wybrani.contains(sprawozdawcaLogin) || sprawozdawcaLogin.equals(przewodniczacyLogin)) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Sprawozdawca musi być jednym z wybranych sędziów, innym niż przewodniczący.", null));
            return null;
        }
        if (posiedzenie == null || posiedzenie.isBefore(clock.today())) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Podaj termin posiedzenia niejawnego (nie wcześniej niż dziś).", null));
            return null;
        }
        // kolejność w składzie: przewodniczący, sprawozdawca, trzeci sędzia
        List<String> sklad = new ArrayList<>();
        sklad.add(przewodniczacyLogin);
        sklad.add(sprawozdawcaLogin);
        wybrani.stream().filter(l -> !sklad.contains(l)).forEach(sklad::add);
        try {
            appeals.referToPanel(appealId, sklad, posiedzenie, currentUser.getLogin(), currentUser.getRoles());
        } catch (RuntimeException e) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się skierować zażalenia do składu.", e.getMessage()));
            return null;
        }
        fc.getExternalContext().getFlash().setKeepMessages(true);
        fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Zażalenie " + sprawa.sygnatura().format() + " skierowano do składu.",
                "Posiedzenie niejawne " + posiedzenie + "."));
        return "/rejestry/zazalenia.xhtml?faces-redirect=true";
    }

    public boolean isRzeczywisty() { return !settings.isDemo() && sprawa != null; }
    public boolean isMaZazalenie() { return appealId != null; }
    public boolean isSkladJuzWyznaczony() { return skladZapisany != null && !skladZapisany.isBlank(); }
    public String getSkladZapisany() {
        if (skladZapisany == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : skladZapisany.split(",")) {
            sb.append(sb.length() == 0 ? "" : ", ").append(judgeDictionary.displayName(l.trim()));
        }
        return sb.toString();
    }
    public List<Kandydat> getKandydaci() { return kandydaci; }
    public List<Kandydat> getKandydaciDostepni() { return kandydaci.stream().filter(k -> !k.wykluczony()).toList(); }
    public List<String> getWybrani() { return wybrani; }
    public void setWybrani(List<String> w) { wybrani = w == null ? new ArrayList<>() : new ArrayList<>(w); }
    public String getPrzewodniczacyLogin() { return przewodniczacyLogin; }
    public void setPrzewodniczacyLogin(String p) { przewodniczacyLogin = p; }
    public String getSprawozdawcaLogin() { return sprawozdawcaLogin; }
    public void setSprawozdawcaLogin(String s) { sprawozdawcaLogin = s; }
    public String getSygnaturaParam() { return sygnaturaParam; }
    public void setSygnaturaParam(String s) { sygnaturaParam = s; }
    public LocalDate getWniesiono() { return wniesiono; }
    public Boolean getWTerminie() { return wTerminie; }

    public boolean isPoTerminie() {
        if (isRzeczywisty()) {
            return Boolean.FALSE.equals(wTerminie);
        }
        return "po".equals(stan) || "po-terminie".equals(stan);
    }
    public String getSygnatura() {
        if (isRzeczywisty()) {
            return sprawa.sygnatura().format();
        }
        return isPoTerminie() ? "Ts 240/26" : "Ts 301/26";
    }
    public String getSkarzacy() {
        if (isRzeczywisty()) {
            return sprawa.skarzacy() == null ? "" : sprawa.skarzacy();
        }
        return isPoTerminie() ? "Lech Zmyślony" : "Robert Fikcyjny";
    }
    public String getOdmowa() {
        if (isRzeczywisty()) {
            return "postanowienie o odmowie · " + sedziaOdmowy;
        }
        return isPoTerminie() ? "postanowienie z 15.08.2026 · sędzia TK Maria Makietowa" : "postanowienie z 21.10.2026 · sędzia TK Adam Testowy";
    }
    public String getDoreczenieOdmowy() {
        if (isRzeczywisty()) {
            return "zob. rejestr doręczeń";
        }
        return isPoTerminie() ? "20.08.2026" : "28.10.2026 · doręczenie zastępcze";
    }
    public String getWniesienie() {
        if (isRzeczywisty()) {
            return wniesiono == null ? "—" : wniesiono + (wTerminie == null ? " · terminowość do oceny" : wTerminie ? " · w terminie (pomocniczo)" : " · po terminie (pomocniczo)");
        }
        return isPoTerminie() ? "02.11.2026 · DW 2026/04806 (ponowne zażalenie)" : "03.11.2026 · DW 2026/04813 · e-Doręczenia";
    }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getPrzewodniczacy() { return przewodniczacy; }
    public void setPrzewodniczacy(String p) { this.przewodniczacy = p; }
    public String getSprawozdawca() { return sprawozdawca; }
    public void setSprawozdawca(String s) { this.sprawozdawca = s; }
    public LocalDate getPosiedzenie() { return posiedzenie; }
    public void setPosiedzenie(LocalDate posiedzenie) { this.posiedzenie = posiedzenie; }
}
