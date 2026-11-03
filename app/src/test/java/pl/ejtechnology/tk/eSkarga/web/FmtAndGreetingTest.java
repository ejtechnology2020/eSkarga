package pl.ejtechnology.tk.eSkarga.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.web.view.PulpitBean;

/** Teksty terminów i odmiana po polsku (status i termin nigdy samym kolorem). */
class FmtAndGreetingTest {

    private final Fmt fmt = new Fmt();

    @Test
    void daysTextForms() {
        assertThat(fmt.daysText(6)).isEqualTo("za 6 dni");
        assertThat(fmt.daysText(1)).isEqualTo("jutro");
        assertThat(fmt.daysText(0)).isEqualTo("dziś");
        assertThat(fmt.daysText(-4)).isEqualTo("4 dni po terminie");
        assertThat(fmt.daysText(-1)).isEqualTo("1 dzień po terminie");
    }

    @Test
    void untilTextUsesPolishDateFormat() {
        assertThat(fmt.untilText(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 3))).isEqualTo("do 09.11.2026 · za 6 dni");
    }

    @Test
    void taskCountDeclension() {
        assertThat(PulpitBean.tasksLabel(1)).isEqualTo("1 zadanie");
        assertThat(PulpitBean.tasksLabel(3)).isEqualTo("3 zadania");
        assertThat(PulpitBean.tasksLabel(5)).isEqualTo("5 zadań");
        assertThat(PulpitBean.tasksLabel(22)).isEqualTo("22 zadania");
        assertThat(PulpitBean.tasksLabel(12)).isEqualTo("12 zadań");
    }

    @Test
    void vocativeOfDemoNames() {
        assertThat(PulpitBean.vocative("Marek")).isEqualTo("Marku");
        assertThat(PulpitBean.vocative("Tomasz")).isEqualTo("Tomaszu");
        assertThat(PulpitBean.vocative("Karolina")).isEqualTo("Karolino");
    }
}
