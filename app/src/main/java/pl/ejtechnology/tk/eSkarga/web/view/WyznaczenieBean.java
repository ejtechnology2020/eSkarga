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
    private String sedzia = "Adam Testowy";

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
            var cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElseThrow(() -> new IllegalArgumentException("Wybierz sprawę"));
            judges.assign(cf.getId(), sedzia, currentUser.getLogin(), currentUser.getRoles());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Sędzia wyznaczony.", sedzia + " · " + sygnatura));
            return "/akta/podglad.xhtml?faces-redirect=true&sygnatura=" + sygnatura.replace(" ", "%20");
        } catch (SecurityException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odmowa: brak ważnego upoważnienia (BR-10).", e.getMessage()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się wyznaczyć sędziego.", e.getMessage()));
        }
        return null;
    }
    public String getSygnatura() { return sygnatura; }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }

    public List<JudgeLoad> getSedziowie() {
        return data.judgeLoads();
    }

    public String getUpowaznienie() { return upowaznienie; }
    public void setUpowaznienie(String upowaznienie) { this.upowaznienie = upowaznienie; }
    public String getSedzia() { return sedzia; }
    public void setSedzia(String sedzia) { this.sedzia = sedzia; }
}
