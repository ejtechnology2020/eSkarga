package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.Tone;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.Fmt;

/**
 * S-09 Korekta wpisu i akceptacja (UC-22, FR-08, NFR-01): porównanie przed/po, uzasadnienie wymagane,
 * akceptacja kierownika tworzy nową wersję wpisu – bez UPDATE na tabelach repertorium.
 */
@Named("korekta")
@ViewScoped
public class KorektaBean implements Serializable {

    /** Wiersz porównania pola przed i po korekcie. */
    public record Diff(String pole, String przed, String po, boolean zmienione) implements Serializable {
    }

    @Inject
    private CurrentUser currentUser;
    @Inject
    private Fmt fmt;

    private String stan;
    private String uzasadnienie = "Błąd pisowni w nazwisku pełnomocnika i niepełny przedmiot. Skarga wskazuje także art. 86 ust. 1 ustawy o VAT (k. 3 akt).";
    private String uwagi;
    private boolean zatwierdzono;
    private boolean wyslano;

    public void applyState() {
        if ("blad".equals(stan)) {
            uzasadnienie = "";
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.correction.justification"),
                    "Bez niego wniosek nie trafi do kierownika."));
        } else if ("zatwierdzono".equals(stan)) {
            zatwierdzono = true;
        }
    }

    public boolean isKierownik() {
        return currentUser.getRole() == AppRole.TK_KIEROWNIK;
    }

    public void send() {
        if (uzasadnienie == null || uzasadnienie.isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.correction.justification"),
                    "Bez niego wniosek nie trafi do kierownika."));
            return;
        }
        wyslano = true;
    }

    public void approve() {
        zatwierdzono = true;
    }

    public List<Diff> getDiff() {
        return List.of(
                new Diff("Sygnatura", "Ts 318/26", null, false),
                new Diff("Pełnomocnik", "r.pr. Olenka Testowa", "r.pr. Oleńka Testowa", true),
                new Diff("Przedmiot", "art. 15 ust. 2 ustawy o podatku od towarów i usług", "art. 15 ust. 2 i art. 86 ust. 1 ustawy o podatku od towarów i usług", true),
                new Diff("Skarżący", "Przykładowa Spółka z o.o.", null, false),
                new Diff("Data wpisu", "28.10.2026", null, false));
    }

    public String getStatusText() {
        return zatwierdzono ? "zatwierdzona" : (isKierownik() || wyslano) ? "czeka na akceptację" : "szkic";
    }

    public Tone getStatusTone() {
        return zatwierdzono ? Tone.OK : (isKierownik() || wyslano) ? Tone.WARN : Tone.NEUTRAL;
    }

    public String getEyebrow() {
        return isKierownik() ? "widok kierownika" : zatwierdzono ? "zatwierdzono" : "wniosek referenta";
    }

    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getUzasadnienie() { return uzasadnienie; }
    public void setUzasadnienie(String uzasadnienie) { this.uzasadnienie = uzasadnienie; }
    public String getUwagi() { return uwagi; }
    public void setUwagi(String uwagi) { this.uwagi = uwagi; }
    public boolean isZatwierdzono() { return zatwierdzono; }
    public boolean isWyslano() { return wyslano; }
}
