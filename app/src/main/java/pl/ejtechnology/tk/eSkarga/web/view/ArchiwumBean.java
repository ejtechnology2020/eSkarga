package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.ArchiveService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.ArchiveRow;
import pl.ejtechnology.tk.eSkarga.web.model.Effect;

/** S-21 Przekazanie do archiwum (UC-21, FR-34): kategoria archiwalna z konfiguracji, spis zdawczo-odbiorczy, błąd braku PDF/A. */
@Named("archiwum")
@ViewScoped
public class ArchiwumBean implements Serializable {

    @Inject
    private ViewData data;

    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private ArchiveService archive;
    @Inject
    private CaseFileService cases;
    private String stan;
    private boolean dialog;
    private String sygnatura;
    private String ostatniSpis;
    /** UC-21: paczka archiwalna (metadata.xml + SHA256SUMS + PDF/A) z Object Lock i spis zdawczo-odbiorczy. */
    public void transfer() {
        if (settings.isDemo()) {
            dialog = false;
            return;
        }
        try {
            var cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElseThrow(() -> new IllegalArgumentException("Wybierz sprawę"));
            var t = archive.transfer(cf.getId(), currentUser.getLogin());
            ostatniSpis = t.getTransferNo();
            dialog = false;
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Akta przekazane do archiwum.", "Spis " + t.getTransferNo() + " · kategoria " + t.getCategory()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się przekazać akt.", e.getMessage()));
        }
    }
    public String getSygnatura() { return sygnatura; }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }
    public String getOstatniSpis() { return ostatniSpis; }

    public boolean isBladPdfa() { return "blad".equals(stan); }
    public List<ArchiveRow> getRows() { return data.archive(isBladPdfa()); }
    public List<Effect> getSkutki() {
        return List.of(Effect.next("Akta Ts 240/26 (34 karty) przejdą do archiwum zakładowego, spis nr 14/2026."),
                Effect.warning("Sprawa będzie tylko do odczytu. Przywrócenie wymaga wypożyczenia akt."));
    }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public boolean isDialog() { return dialog || "okno".equals(stan); }
    public void setDialog(boolean dialog) { this.dialog = dialog; if (!dialog) { stan = null; } }
}
