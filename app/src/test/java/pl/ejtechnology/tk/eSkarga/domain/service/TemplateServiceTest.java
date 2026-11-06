package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.model.TemplateStatus;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** S-24 (T-026a, D13): poprawka projektu w miejscu, nowa wersja z aktywnej, wycofanie, usunięcie logiczne (rola bez DELETE), lista bez usuniętych. */
class TemplateServiceTest extends ServiceTestSupport {


    @Test
    void projektPoprawianyWMiejscuAktywnaTylkoNowaWersja() {
        final String KOD = "TEST_PROJEKT";   // osobny kod na test – baza H2 jest wspólna dla klasy
        LetterTemplate v1 = tx(s -> s.templates.saveDraft(KOD, "Odmowa", "<p>${sygnatura}</p>", "autor"));
        tx(s -> s.templates.updateDraft(v1.getId(), "Odmowa v1 poprawiona", "<p>${sygnatura} – ${skarzacy}</p>", "autor"));
        run(s -> {
            LetterTemplate t = s.templates.require(v1.getId());
            assertThat(t.getName()).isEqualTo("Odmowa v1 poprawiona");
            assertThat(t.getSourceXhtml()).contains("skarzacy");
            assertThat(t.getVersion()).as("poprawka projektu nie tworzy wersji").isEqualTo(1);
        });
        assertThat(tx(s -> s.templates.activate(v1.getId(), "druga.osoba")).ok()).isTrue();
        assertThatThrownBy(() -> tx(s -> s.templates.updateDraft(v1.getId(), "x", "<p>y</p>", "autor")))
                .hasMessageContaining("tylko projekt");
        LetterTemplate v2 = tx(s -> s.templates.saveDraft(KOD, "Odmowa", "<p>${sygnatura} v2</p>", "autor"));
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getStatus()).isEqualTo(TemplateStatus.PROJEKT);
        run(s -> assertThat(s.templates.active(KOD)).map(LetterTemplate::getId).hasValue(v1.getId()));
    }

    @Test
    void wycofanieIUsuniecieLogiczne() {
        final String KOD = "TEST_WYCOFANIE";
        LetterTemplate v1 = tx(s -> s.templates.saveDraft(KOD, "Odmowa", "<p>${sygnatura}</p>", "autor"));
        tx(s -> s.templates.activate(v1.getId(), "druga.osoba"));
        assertThatThrownBy(() -> tx(s -> s.templates.delete(v1.getId(), "admin"))).hasMessageContaining("Aktywnej wersji nie można usunąć");
        assertThatThrownBy(() -> tx(s -> s.templates.withdraw(v1.getId() + 999, "admin"))).isInstanceOf(IllegalArgumentException.class);

        tx(s -> s.templates.withdraw(v1.getId(), "admin"));
        run(s -> {
            assertThat(s.templates.active(KOD)).as("brak aktywnej wersji po wycofaniu").isEmpty();
            assertThat(s.templates.require(v1.getId()).getStatus()).isEqualTo(TemplateStatus.WYCOFANA);
            assertThat(s.templates.all()).extracting(LetterTemplate::getId).contains(v1.getId());
        });
        LetterTemplate projekt = tx(s -> s.templates.saveDraft(KOD, "Odmowa", "<p>${sygnatura} v2</p>", "autor"));
        tx(s -> s.templates.delete(v1.getId(), "admin"));
        tx(s -> s.templates.delete(projekt.getId(), "admin"));
        run(s -> {
            assertThat(s.templates.require(v1.getId()).getStatus()).as("wiersz zostaje – pisma pamiętają wersję (D13)").isEqualTo(TemplateStatus.USUNIETA);
            assertThat(s.templates.all()).extracting(LetterTemplate::getId).doesNotContain(v1.getId(), projekt.getId());
            assertThat(s.templates.usageCount(v1.getId())).isZero();
            assertThat(s.templates.usageCounts()).doesNotContainKey(v1.getId());
        });
        LetterTemplate v3 = tx(s -> s.templates.saveDraft(KOD, "Odmowa", "<p>${sygnatura} v3</p>", "autor"));
        assertThat(v3.getVersion()).as("numer wersji liczony także z usuniętych – bez kolizji unikalności (code, version)").isEqualTo(projekt.getVersion() + 1);
    }
}
