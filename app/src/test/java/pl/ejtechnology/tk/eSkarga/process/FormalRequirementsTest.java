package pl.ejtechnology.tk.eSkarga.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.test.Deployment;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import pl.ejtechnology.tk.eSkarga.domain.model.Profession;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

/** UC-06, FR-11, BR-05: tabela DMN wymogów formalnych (T-024). */
@Deployment(resources = "bpmn/wymogi-formalne.dmn")
class FormalRequirementsTest {

    @RegisterExtension
    static ProcessEngineExtension extension = ProcessEngineExtension.builder().build();

    private FormalRequirementsService service() {
        return Wiring.inject(new FormalRequirementsService(), extension.getDecisionService());
    }

    @Test
    void skargaBezPelnomocnikaMaWymogArt44() {
        List<FormalRequirementsService.Requirement> reqs = service().requirements(ProcessVariables.RodzajPisma.SKARGA, false, null);
        assertThat(reqs).extracting(FormalRequirementsService.Requirement::code)
                .containsExactly("S-01", "S-02", "S-03", "S-04", "S-05", "S-06", "S-07", "S-08", "S-09");
        assertThat(reqs).filteredOn(r -> r.code().equals("S-09")).singleElement().satisfies(r -> assertThat(r.legalBasis()).isEqualTo("art. 44 ust. 1"));
        assertThat(reqs).allSatisfy(r -> assertThat(r.legalBasis()).startsWith("art."));
    }

    @Test
    void skargaSporzadzonaPrzezAdwokataNieMaWymoguZastepstwa() {
        List<FormalRequirementsService.Requirement> reqs = service().requirements(ProcessVariables.RodzajPisma.SKARGA, true, null);
        assertThat(reqs).extracting(FormalRequirementsService.Requirement::code).hasSize(8).doesNotContain("S-09");
    }

    @Test
    void skarzacySedziaDzialaSam() {
        // BR-05: sędzia, prokurator, adwokat, radca, notariusz, profesor lub doktor habilitowany nauk prawnych
        List<FormalRequirementsService.Requirement> reqs = service().requirements(ProcessVariables.RodzajPisma.SKARGA, false, Profession.SEDZIA);
        assertThat(reqs).extracting(FormalRequirementsService.Requirement::code).doesNotContain("S-09");
        assertThat(FormalRequirementsService.representationSatisfied(false, Profession.PROFESOR_PRAWA)).isTrue();
        assertThat(FormalRequirementsService.representationSatisfied(false, null)).isFalse();
        assertThat(FormalRequirementsService.representationSatisfied(true, null)).isTrue();
    }

    @Test
    void wniosekMaWymogiZArt47i48() {
        List<FormalRequirementsService.Requirement> reqs = service().requirements(ProcessVariables.RodzajPisma.WNIOSEK, false, null);
        assertThat(reqs).extracting(FormalRequirementsService.Requirement::code).containsExactly("W-01", "W-02", "W-03", "W-04", "W-05", "W-06", "W-07");
        assertThat(service().requirements(ProcessVariables.RodzajPisma.WNIOSEK, true, null)).extracting(FormalRequirementsService.Requirement::code).doesNotContain("W-07");
    }

    @Test
    void pismoOgolneNieMaWymogow() {
        assertThat(service().requirements(ProcessVariables.RodzajPisma.OGOLNE, false, null)).isEmpty();
    }
}
