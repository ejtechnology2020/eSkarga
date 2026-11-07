package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.RequirementCondition;
import pl.ejtechnology.tk.eSkarga.domain.model.FormalRequirement;
import pl.ejtechnology.tk.eSkarga.process.FormalRequirementsService;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

/** S-24 (UC-06, ADR 0015): słownik wymogów Ts/Tw zasiany z DMN, edycja i wycofanie przez administratora, lista kontrolna z warunkami. */
class RequirementCatalogServiceTest extends ServiceTestSupport {

    private FormalRequirementsService lista(Services s) {
        return Wiring.inject(new FormalRequirementsService(), engine.getDecisionService(), s.requirements);
    }

    @Test
    void zasiewZDmnIWarunkiStosowania() {
        run(s -> {
            // baza H2 wspólna dla klasy – drugi test dopisuje S-10, dlatego sprawdzamy pozycje zasiewu (S-0x) z zachowaniem kolejności
            assertThat(s.requirements.active(DocketSymbol.TS)).extracting(FormalRequirement::getCode).filteredOn(c -> c.matches("S-0\\d"))
                    .containsExactly("S-01", "S-02", "S-03", "S-04", "S-05", "S-06", "S-07", "S-08", "S-09");
            assertThat(s.requirements.active(DocketSymbol.TW)).extracting(FormalRequirement::getCode).containsExactly("W-01", "W-02", "W-03", "W-04", "W-05", "W-06", "W-07");
            FormalRequirementsService f = lista(s);
            assertThat(f.requirements(ProcessVariables.RodzajPisma.SKARGA, true, null)).extracting(FormalRequirementsService.Requirement::code)
                    .as("z adwokatem bez S-09 (BR-05)").doesNotContain("S-09").filteredOn(c -> c.matches("S-0\\d")).hasSize(8);
            assertThat(f.requirements(ProcessVariables.RodzajPisma.SKARGA, false, null)).extracting(FormalRequirementsService.Requirement::code).contains("S-09");
            assertThat(f.requirements(ProcessVariables.RodzajPisma.SKARGA, false, pl.ejtechnology.tk.eSkarga.domain.model.Profession.ADWOKAT)).extracting(FormalRequirementsService.Requirement::code)
                    .as("skarżący zwolniony z zastępstwa").doesNotContain("S-09");
            assertThat(f.requirements(ProcessVariables.RodzajPisma.WNIOSEK, true, null)).extracting(FormalRequirementsService.Requirement::code).doesNotContain("W-07");
            assertThat(f.requirements(ProcessVariables.RodzajPisma.WNIOSEK, false, null)).extracting(FormalRequirementsService.Requirement::code).contains("W-07");
        });
    }

    @Test
    void administratorDodajeZmieniaIWycofuje() {
        FormalRequirement nowy = tx(s -> s.requirements.add(DocketSymbol.TS, "s-10", "Oświadczenie o wyczerpaniu drogi prawnej", "art. 77 ust. 1", RequirementCondition.ZAWSZE, 85, "admin"));
        assertThat(nowy.getCode()).as("kod normalizowany").isEqualTo("S-10");
        run(s -> assertThat(lista(s).requirements(ProcessVariables.RodzajPisma.SKARGA, true, null)).extracting(FormalRequirementsService.Requirement::code)
                .as("kolejność po pozycji: 85 między S-08 (80) a S-09 (90)").containsSubsequence("S-08", "S-10").doesNotContain("S-09"));
        assertThatThrownBy(() -> tx(s -> s.requirements.add(DocketSymbol.TS, "S-10", "dubel", "art. 1", RequirementCondition.ZAWSZE, 1, "admin"))).hasMessageContaining("już istnieje");
        assertThatThrownBy(() -> tx(s -> s.requirements.add(DocketSymbol.SK, "X-1", "x", "art. 1", RequirementCondition.ZAWSZE, 1, "admin"))).hasMessageContaining("Ts i Tw");
        assertThatThrownBy(() -> tx(s -> s.requirements.add(DocketSymbol.TS, "zły kod!", "x", "art. 1", RequirementCondition.ZAWSZE, 1, "admin"))).hasMessageContaining("Kod wymogu");
        assertThatThrownBy(() -> tx(s -> s.requirements.add(DocketSymbol.TS, "S-11", "x", " ", RequirementCondition.ZAWSZE, 1, "admin"))).hasMessageContaining("Podstawa prawna");

        tx(s -> s.requirements.update(nowy.getId(), "Oświadczenie o wyczerpaniu drogi prawnej (po opinii BSP)", "art. 77 ust. 1 i 2", RequirementCondition.BEZ_PELNOMOCNIKA, 85, "admin"));
        run(s -> {
            FormalRequirement r = s.requirements.require(nowy.getId());
            assertThat(r.getDescription()).contains("po opinii BSP");
            assertThat(r.getCondition()).isEqualTo(RequirementCondition.BEZ_PELNOMOCNIKA);
            assertThat(r.getUpdatedBy()).isEqualTo("admin");
            assertThat(lista(s).requirements(ProcessVariables.RodzajPisma.SKARGA, true, null)).extracting(FormalRequirementsService.Requirement::code).doesNotContain("S-10");
            assertThat(lista(s).requirements(ProcessVariables.RodzajPisma.SKARGA, false, null)).extracting(FormalRequirementsService.Requirement::code).contains("S-10");
        });

        tx(s -> s.requirements.setActive(nowy.getId(), false, "admin"));
        run(s -> {
            assertThat(s.requirements.active(DocketSymbol.TS)).extracting(FormalRequirement::getCode).doesNotContain("S-10");
            assertThat(s.requirements.all(DocketSymbol.TS)).extracting(FormalRequirement::getCode).contains("S-10");
            assertThat(s.audit.recent(20)).extracting(e -> e.getAction()).anySatisfy(a -> assertThat(a).contains("wycofanie wymogu formalnego Ts S-10"));
        });
        tx(s -> s.requirements.setActive(nowy.getId(), true, "admin"));
        run(s -> assertThat(s.requirements.active(DocketSymbol.TS)).extracting(FormalRequirement::getCode).contains("S-10"));
        assertThat(List.of(RequirementCondition.values())).allSatisfy(c -> assertThat(c.label()).isNotBlank());
    }
}
