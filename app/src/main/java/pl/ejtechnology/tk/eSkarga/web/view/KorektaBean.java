package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.service.CorrectionService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
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
    @Inject
    private OidcSettings settings;
    @Inject
    private CaseFileService cases;
    @Inject
    private CorrectionService corrections;
    private String sygnatura = "Ts 318/26";
    private String nowyPrzedmiot;
    private String nowyWzorzec;
    private String nowaPowiazana;

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
        if (!settings.isDemo()) {
            try {
                var cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElseThrow(() -> new IllegalArgumentException("Brak sprawy " + sygnatura));
                corrections.request(cf.getId(), new CorrectionService.Changes(blank(nowyPrzedmiot), blank(nowyWzorzec), blank(nowaPowiazana)), uzasadnienie, currentUser.getLogin());
            } catch (RuntimeException e) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się złożyć wniosku o korektę.", e.getMessage()));
                return;
            }
        }
        wyslano = true;
    }
    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
    public void reject() {
        if (!settings.isDemo()) {
            try {
                var pending = pendingVersion().orElseThrow(() -> new IllegalStateException("Brak korekty oczekującej dla " + sygnatura));
                corrections.reject(pending.getId(), currentUser.getLogin(), uwagi);
            } catch (RuntimeException e) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się odrzucić korekty.", e.getMessage()));
                return;
            }
        }
        wyslano = false;
    }
    private java.util.Optional<pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion> pendingVersion() {
        return cases.byDocket(DocketNumber.parse(sygnatura)).flatMap(cf -> corrections.history(cf.getId()).stream()
                .filter(v -> v.getCorrectionStatus() == pl.ejtechnology.tk.eSkarga.domain.model.CorrectionStatus.OCZEKUJE).findFirst());
    }
    /** Historia wersji wpisu (FR-08) – w trybie demo pusta (dane statyczne w widoku). */
    public List<pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion> getHistoria() {
        if (settings.isDemo()) {
            return List.of();
        }
        return cases.byDocket(DocketNumber.parse(sygnatura)).map(cf -> corrections.history(cf.getId())).orElse(List.of());
    }
    public String getSygnatura() { return sygnatura; }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }
    public String getNowyPrzedmiot() { return nowyPrzedmiot; }
    public void setNowyPrzedmiot(String s) { this.nowyPrzedmiot = s; }
    public String getNowyWzorzec() { return nowyWzorzec; }
    public void setNowyWzorzec(String s) { this.nowyWzorzec = s; }
    public String getNowaPowiazana() { return nowaPowiazana; }
    public void setNowaPowiazana(String s) { this.nowaPowiazana = s; }

    /** UC-22: akceptacja kierownika (druga osoba) – nowa wersja wpisu staje się obowiązująca. */
    public void approve() {
        if (!settings.isDemo()) {
            try {
                var pending = pendingVersion().orElseThrow(() -> new IllegalStateException("Brak korekty oczekującej dla " + sygnatura));
                corrections.approve(pending.getId(), currentUser.getLogin(), currentUser.getRoles());
            } catch (RuntimeException e) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zatwierdzić korekty.", e.getMessage()));
                return;
            }
        }
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
