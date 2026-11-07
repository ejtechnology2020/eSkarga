package pl.ejtechnology.tk.eSkarga.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import org.operaton.bpm.dmn.engine.DmnDecisionTableResult;
import org.operaton.bpm.engine.DecisionService;

import pl.ejtechnology.tk.eSkarga.domain.model.Profession;
import pl.ejtechnology.tk.eSkarga.domain.service.RequirementCatalogService;

/**
 * Lista kontrolna wymogów formalnych (T-024, UC-06, FR-11, FR-13): źródłem jest słownik {@code formal_requirement} zarządzany na S-24
 * ({@link RequirementCatalogService}, ADR 0015 – aktualizacja 07.11.2026); tabela DMN {@code wymogi-formalne} pozostaje jako
 * zasiew i zapas, gdy słownik dla repertorium jest pusty. BR-05: obowiązek zastępstwa przez adwokata lub radcę prawnego
 * (art. 44 ust. 1), chyba że skarżący sam należy do zawodów wymienionych w ustawie.
 */
@Stateless
public class FormalRequirementsService {

    public static final String DECISION_KEY = "Dec_WymogiFormalne";

    /** Wymóg z tabeli decyzyjnej. */
    public record Requirement(String code, String description, String legalBasis) {
    }

    @Inject
    DecisionService decisionService;
    @Inject
    RequirementCatalogService catalog;

    public List<Requirement> requirements(ProcessVariables.RodzajPisma rodzaj, boolean professionalAttorney, Profession complainantProfession) {
        boolean exempt = isExemptFromRepresentation(complainantProfession);
        pl.ejtechnology.tk.eSkarga.domain.DocketSymbol symbol = rodzaj == ProcessVariables.RodzajPisma.WNIOSEK ? pl.ejtechnology.tk.eSkarga.domain.DocketSymbol.TW
                : rodzaj == ProcessVariables.RodzajPisma.SKARGA ? pl.ejtechnology.tk.eSkarga.domain.DocketSymbol.TS : null;
        if (catalog != null && symbol != null) {
            List<pl.ejtechnology.tk.eSkarga.domain.model.FormalRequirement> rows = catalog.active(symbol);
            if (!rows.isEmpty()) {
                List<Requirement> list = new ArrayList<>();
                for (var r : rows) {
                    if (r.getCondition().applies(professionalAttorney, exempt)) {
                        list.add(new Requirement(r.getCode(), r.getDescription(), r.getLegalBasis()));
                    }
                }
                return list;
            }
        }
        DmnDecisionTableResult result = decisionService.evaluateDecisionTableByKey(DECISION_KEY, Map.of(
                "rodzajPisma", rodzaj.name(),
                "pelnomocnikZawodowy", professionalAttorney,
                "skarzacyZwolniony", exempt));
        List<Requirement> list = new ArrayList<>();
        result.forEach(r -> list.add(new Requirement(r.getEntry("kod"), r.getEntry("wymog"), r.getEntry("podstawa"))));
        return list;
    }

    /** BR-05 / art. 44 ust. 1: skarżący będący sędzią, prokuratorem, adwokatem, radcą prawnym, notariuszem, profesorem lub doktorem habilitowanym nauk prawnych działa sam. */
    public static boolean isExemptFromRepresentation(Profession complainantProfession) {
        return complainantProfession != null;
    }

    /** BR-05: czy skarga wymaga pełnomocnika zawodowego i czy wymóg jest spełniony. */
    public static boolean representationSatisfied(boolean professionalAttorney, Profession complainantProfession) {
        return professionalAttorney || isExemptFromRepresentation(complainantProfession);
    }
}
