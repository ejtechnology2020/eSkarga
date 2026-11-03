package pl.ejtechnology.tk.eSkarga.web.view;

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
    private DemoDataset demo;

    private String upowaznienie = "wazne";
    private String sedzia = "Adam Testowy";

    public boolean isUpowaznienieWazne() {
        if ("brak".equals(upowaznienie)) {
            return false;
        }
        var d = new CaseAccessPolicy.Delegation("mmakietowa", CaseAccessPolicy.FUNCTION_PRESIDENT, DemoDataset.d(2026, 10, 30), DemoDataset.d(2026, 12, 31), false);
        return CaseAccessPolicy.canActAsPresident("mmakietowa", Set.of(AppRole.TK_PREZES, AppRole.TK_SEDZIA), Set.of(d), DemoDataset.TODAY);
    }

    public List<CaseSummary> getOczekujace() {
        return List.of(demo.caseBy("Ts 318/26"), demo.caseBy("Ts 319/26"));
    }

    public List<JudgeLoad> getSedziowie() {
        return demo.judgeLoads();
    }

    public String getUpowaznienie() { return upowaznienie; }
    public void setUpowaznienie(String upowaznienie) { this.upowaznienie = upowaznienie; }
    public String getSedzia() { return sedzia; }
    public void setSedzia(String sedzia) { this.sedzia = sedzia; }
}
