package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.domain.service.JudgeAssignmentService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.JudgeLoad;

/**
 * S-17 Wyznaczenie sędziego (UC-05, FR-14, BR-10). Lista spraw oczekujących, tabela sędziów z obciążeniem,
 * podgląd zarządzenia. Bez ważnego upoważnienia akcja jest niedostępna z wyjaśnieniem.
 */
@Named("wyznaczenie")
@ViewScoped
public class WyznaczenieBean implements Serializable {

    @Inject
    private ViewData data;
    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private JudgeAssignmentService judges;
    @Inject
    private DelegationService delegations;
    @Inject
    private CaseFileService cases;
    @Inject
    private AppClock clock;
    private String sygnatura;

    private String upowaznienie = "wazne";
    /** Wybrany sędzia: poza demo login z ID-users (JudgeLoad.sedzia), w demo nazwisko z danych przykładowych. */
    private String sedzia;

    public boolean isUpowaznienieWazne() {
        if (!settings.isDemo()) {
            return CaseAccessPolicy.canActAsPresident(currentUser.getLogin(), currentUser.getRoles(), delegations.policyDelegations(currentUser.getLogin()), clock.today());
        }
        if ("brak".equals(upowaznienie)) {
            return false;
        }
        var d = new CaseAccessPolicy.Delegation("mmakietowa", CaseAccessPolicy.FUNCTION_PRESIDENT, DemoDataset.d(2026, 10, 30), DemoDataset.d(2026, 12, 31), false);
        return CaseAccessPolicy.canActAsPresident("mmakietowa", Set.of(AppRole.TK_PREZES, AppRole.TK_SEDZIA), Set.of(d), DemoDataset.TODAY);
    }

    public List<CaseSummary> getOczekujace() {
        if (!settings.isDemo()) {
            return judges.awaiting().stream().map(cf -> data.caseBy(cf.getDocket().format())).filter(java.util.Objects::nonNull).toList();
        }
        return List.of(data.caseBy("Ts 318/26"), data.caseBy("Ts 319/26"));
    }
    /** UC-05 (BR-10): zarządzenie w aktach, przydział, assignee zadania kontroli, zdarzenie audytu – jedna transakcja. */
    public String assign() {
        if (settings.isDemo()) {
            return "/czynnosci/podpis.xhtml?faces-redirect=true&stan=1";
        }
        try {
            String syg = getSygnatura();
            if (syg == null || syg.isBlank()) {
                throw new IllegalArgumentException("Wybierz sprawę oczekującą na wyznaczenie");
            }
            var cf = cases.byDocket(DocketNumber.parse(syg)).orElseThrow(() -> new IllegalArgumentException("Wybierz sprawę"));
            if (sedzia == null || sedzia.isBlank()) {
                throw new IllegalArgumentException("Wybierz sędziego z listy");
            }
            // projekt zarządzenia z szablonu w aktach; wyznaczenie staje się skuteczne po podpisie na S-16 (D10)
            var act = judges.draftOrder(cf.getId(), sedzia, getSedziaNazwa(), currentUser.getLogin(), currentUser.getRoles());
            FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Projekt zarządzenia w aktach – podpisz go.", getSedziaNazwa() + " · " + syg));
            return "/czynnosci/podpis.xhtml?faces-redirect=true&stan=1&dokument=" + act.getDocument().getId() + "&rodzaj=" + pl.ejtechnology.tk.eSkarga.domain.service.LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO.name();
        } catch (SecurityException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odmowa: brak ważnego upoważnienia (BR-10).", e.getMessage()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się wyznaczyć sędziego.", e.getMessage()));
        }
        return null;
    }
    /** Wybrana sprawa; bez wyboru – pierwsza oczekująca (poza demo), żeby „Wyznacz i podpisz” miało na czym działać. */
    public String getSygnatura() {
        if ((sygnatura == null || sygnatura.isBlank()) && !settings.isDemo()) {
            List<CaseSummary> oczekujace = getOczekujace();
            sygnatura = oczekujace.isEmpty() ? null : oczekujace.get(0).sygnaturaText();
        }
        return sygnatura;
    }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }

    public java.time.LocalDate getDzis() { return clock.today(); }

    /** Aktywny szablon zarządzenia (rejestr szablonów pism S-24) – null, gdy nie załadowano szablonów domyślnych. */
    public pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate getSzablonZarzadzenia() {
        try {
            return settings.isDemo() ? null : judges.orderTemplate();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Adres podglądu projektu zarządzenia z szablonu (OrderPreviewServlet) – gdy wybrano sprawę i sędziego. */
    public String getPodgladUrl() {
        String syg = getSygnatura();
        if (settings.isDemo() || syg == null || getSedzia() == null) {
            return null;
        }
        return "/czynnosci/wyznaczenie/podglad?sygnatura=" + java.net.URLEncoder.encode(syg, java.nio.charset.StandardCharsets.UTF_8)
                + "&sedzia=" + java.net.URLEncoder.encode(getSedzia(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Upoważnienie ważne dziś (do banera): poza demo z rejestru upoważnień, w demo z danych przykładowych. */
    public CaseAccessPolicy.Delegation getAktualneUpowaznienie() {
        if (settings.isDemo()) {
            return new CaseAccessPolicy.Delegation("mmakietowa", CaseAccessPolicy.FUNCTION_PRESIDENT, DemoDataset.d(2026, 10, 30), DemoDataset.d(2026, 12, 31), false);
        }
        java.time.LocalDate today = clock.today();
        return delegations.policyDelegations(currentUser.getLogin()).stream()
                .filter(d -> CaseAccessPolicy.FUNCTION_PRESIDENT.equals(d.function()) && d.validOn(today))
                .sorted(java.util.Comparator.comparing(CaseAccessPolicy.Delegation::to).reversed()).findFirst().orElse(null);
    }

    public List<JudgeLoad> getSedziowie() {
        return data.judgeLoads();
    }

    public String getUpowaznienie() { return upowaznienie; }
    public void setUpowaznienie(String upowaznienie) { this.upowaznienie = upowaznienie; }
    public String getSedzia() {
        if (sedzia == null && settings.isDemo()) {
            return "Adam Testowy";
        }
        return sedzia;
    }
    public void setSedzia(String sedzia) { this.sedzia = sedzia; }

    /** Nazwisko wybranego sędziego do podglądu zarządzenia (poza demo wybór jest loginem). */
    public String getSedziaNazwa() {
        String s = getSedzia();
        if (s == null) {
            return "…";
        }
        return getSedziowie().stream().filter(j -> s.equals(j.sedzia()) || s.equals(j.nazwisko())).map(JudgeLoad::nazwisko).findFirst().orElse(s);
    }
}
