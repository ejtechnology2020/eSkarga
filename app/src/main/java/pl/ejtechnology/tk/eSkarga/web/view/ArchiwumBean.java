package pl.ejtechnology.tk.eSkarga.web.view;

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
    private DemoDataset demo;

    private String stan;
    private boolean dialog;

    public boolean isBladPdfa() { return "blad".equals(stan); }
    public List<ArchiveRow> getRows() { return demo.archive(isBladPdfa()); }
    public List<Effect> getSkutki() {
        return List.of(Effect.next("Akta Ts 240/26 (34 karty) przejdą do archiwum zakładowego, spis nr 14/2026."),
                Effect.warning("Sprawa będzie tylko do odczytu. Przywrócenie wymaga wypożyczenia akt."));
    }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public boolean isDialog() { return dialog || "okno".equals(stan); }
    public void setDialog(boolean dialog) { this.dialog = dialog; if (!dialog) { stan = null; } }
}
