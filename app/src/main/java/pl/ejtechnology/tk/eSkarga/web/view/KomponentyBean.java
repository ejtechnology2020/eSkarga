package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.CaseFlag;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineState;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;
import pl.ejtechnology.tk.eSkarga.web.model.Effect;

/** Arkusz komponentów (strona deweloperska). */
@Named("komponenty")
@RequestScoped
public class KomponentyBean implements Serializable {

    @Inject
    private Fmt fmt;

    public CaseStatus[] getStatusy() {
        return CaseStatus.values();
    }

    public CaseFlag[] getFlagi() {
        return CaseFlag.values();
    }

    public List<DeadlineView> getTerminy() {
        return List.of(
                new DeadlineView(DeadlineType.BRAKI, DeadlineState.NORMAL, DemoDataset.d(2026, 11, 9), 14, fmt.untilText(DemoDataset.d(2026, 11, 9), DemoDataset.TODAY), true),
                new DeadlineView(DeadlineType.ZAZALENIE, DeadlineState.WARNING, DemoDataset.d(2026, 11, 4), 86, fmt.untilText(DemoDataset.d(2026, 11, 4), DemoDataset.TODAY) + " (za 1 dzień)", true),
                new DeadlineView(DeadlineType.ZWROTKA, DeadlineState.OVERDUE, null, 100, "16 dni bez potwierdzenia · 2 dni po terminie", true),
                new DeadlineView(DeadlineType.OCZEKIWANIE, DeadlineState.SUSPENDED, null, 40, fmt.msg("deadline.suspendedSince", "21.10.2026"), false),
                new DeadlineView(DeadlineType.ART_77, DeadlineState.AUXILIARY, DemoDataset.d(2026, 10, 20), 100, "upłynąłby 20.10.2026 · ocenia sędzia", true));
    }

    public List<Effect> getSkutki() {
        return List.of(Effect.next("Pismo trafi do adw. Anny Testowej przez e-Doręczenia."), Effect.timer("Termin 7 dni ruszy po doręczeniu."),
                Effect.warning("Wysyłki nie można cofnąć."));
    }
}
