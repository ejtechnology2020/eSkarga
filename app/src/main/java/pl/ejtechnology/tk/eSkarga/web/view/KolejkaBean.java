package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.service.GeneralLetterService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.QueueItem;

/**
 * S-06 Kolejka pism i kwalifikacja (UC-02, UC-04). Referent decyduje, czym jest pismo: nowa skarga (Ts),
 * nowy wniosek (Tw), pismo do sprawy w toku, pismo ogólne. Pismo do sprawy w toku wysyła komunikat do procesu
 * ({@code Msg_Uzupelnienie} albo {@code Msg_Zazalenie}) po kluczu biznesowym = sygnatura (T-013).
 */
@Named("kolejka")
@ViewScoped
public class KolejkaBean implements Serializable {

    @Inject
    private ViewData data;
    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private IncomingRegister incoming;
    @Inject
    private CaseFileService cases;
    @Inject
    private GeneralLetterService generalLetters;
    private String temat = "";
    private int strony = 1;

    private List<QueueItem> items;
    private QueueItem selected;
    private LetterQualification decyzja = LetterQualification.PISMO_DO_SPRAWY;
    private String sygnatura = "Ts 312/26";
    private String rodzaj = "uzupelnienie";
    private String sort = "najstarsze";

    @PostConstruct
    void init() {
        items = data.queue();
        selected = items.stream().filter(QueueItem::wybrane).findFirst().orElse(items.isEmpty() ? null : items.get(0));
    }

    public CaseSummary getSprawa() {
        return data.caseBy(sygnatura);
    }

    public void select(QueueItem it) {
        selected = it;
        skany = null;
    }

    private transient List<pl.ejtechnology.tk.eSkarga.web.model.ScanRow> skany;

    /** Skany wybranego pisma (z bazy); pierwszy PDF jest podglądem, pozostałe do pobrania. */
    public List<pl.ejtechnology.tk.eSkarga.web.model.ScanRow> getSkany() {
        if (skany == null) {
            skany = selected == null ? List.of() : data.scansOf(selected.nr());
        }
        return skany;
    }

    public pl.ejtechnology.tk.eSkarga.web.model.ScanRow getPierwszyPdf() {
        return getSkany().stream().filter(pl.ejtechnology.tk.eSkarga.web.model.ScanRow::pdf).findFirst().orElse(null);
    }

    /** UC-02: kwalifikacja pisma; założenie sprawy w S-07, pismo do sprawy = kolejne karty + komunikat do procesu, pismo ogólne = rejestr z terminem odpowiedzi. */
    public String qualify() {
        if (decyzja == LetterQualification.NOWA_SKARGA_TS || decyzja == LetterQualification.NOWY_WNIOSEK_TW) {
            return "/sprawy/zalozenie.xhtml?faces-redirect=true" + (selected == null ? "" : "&pismo=" + selected.nr().replace(" ", "%20").replace("/", "%2F"))
                    + "&repertorium=" + (decyzja == LetterQualification.NOWA_SKARGA_TS ? "TS" : "TW");
        }
        if (!settings.isDemo() && selected != null) {
            try {
                long letterId = incoming.byRegisterNo(selected.nr()).orElseThrow(() -> new IllegalArgumentException("Brak pisma " + selected.nr())).getId();
                if (decyzja == LetterQualification.PISMO_OGOLNE) {
                    generalLetters.register(letterId, temat == null || temat.isBlank() ? "pismo ogólne" : temat, currentUser.getLogin());
                    return "/rejestry/pisma-ogolne.xhtml?faces-redirect=true";
                }
                var cf = cases.byDocket(DocketNumber.parse(sygnatura)).orElseThrow(() -> new IllegalArgumentException("Brak sprawy " + sygnatura));
                cases.attachLetter(cf.getId(), letterId, "zazalenie".equals(rodzaj) ? "Zażalenie" : "Pismo uzupełniające", Math.max(1, strony), "zazalenie".equals(rodzaj), currentUser.getLogin());
            } catch (RuntimeException e) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zakwalifikować pisma.", e.getMessage()));
                return null;
            }
        }
        return "/akta/podglad.xhtml?faces-redirect=true&sygnatura=" + sygnatura.replace(" ", "%20");
    }

    public String getTemat() { return temat; }
    public void setTemat(String temat) { this.temat = temat; }
    public int getStrony() { return strony; }
    public void setStrony(int strony) { this.strony = strony; }

    public List<QueueItem> getItems() { return items; }
    public QueueItem getSelected() { return selected; }
    public LetterQualification getDecyzja() { return decyzja; }
    public void setDecyzja(LetterQualification decyzja) { this.decyzja = decyzja; }
    public String getSygnatura() { return sygnatura; }
    public void setSygnatura(String sygnatura) { this.sygnatura = sygnatura; }
    public String getRodzaj() { return rodzaj; }
    public void setRodzaj(String rodzaj) { this.rodzaj = rodzaj; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public LetterQualification[] getDecyzje() {
        return new LetterQualification[] {LetterQualification.NOWA_SKARGA_TS, LetterQualification.NOWY_WNIOSEK_TW,
                LetterQualification.PISMO_DO_SPRAWY, LetterQualification.PISMO_OGOLNE};
    }
    public boolean isDoSprawy() { return decyzja == LetterQualification.PISMO_DO_SPRAWY; }
}
