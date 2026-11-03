package pl.ejtechnology.tk.eSkarga.web.view;

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
    private DemoDataset demo;

    private List<QueueItem> items;
    private QueueItem selected;
    private LetterQualification decyzja = LetterQualification.PISMO_DO_SPRAWY;
    private String sygnatura = "Ts 312/26";
    private String rodzaj = "uzupelnienie";
    private String sort = "najstarsze";

    @PostConstruct
    void init() {
        items = demo.queue();
        selected = items.stream().filter(QueueItem::wybrane).findFirst().orElse(items.get(0));
    }

    public CaseSummary getSprawa() {
        return demo.caseBy(sygnatura);
    }

    public void select(QueueItem it) {
        selected = it;
    }

    public String qualify() {
        // Docelowo: CaseFileService.attachLetter / createCase w jednej transakcji (D1) + zdarzenie audytu.
        if (decyzja == LetterQualification.NOWA_SKARGA_TS || decyzja == LetterQualification.NOWY_WNIOSEK_TW) {
            return "/sprawy/zalozenie.xhtml?faces-redirect=true";
        }
        return "/akta/podglad.xhtml?faces-redirect=true&sygnatura=" + sygnatura.replace(" ", "%20");
    }

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
