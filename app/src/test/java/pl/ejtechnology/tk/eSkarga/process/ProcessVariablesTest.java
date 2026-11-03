package pl.ejtechnology.tk.eSkarga.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** D8 (T-020): żadna zmienna procesu nie zawiera danych osobowych – test nazw i typów. */
class ProcessVariablesTest {

    @Test
    void allowedNamesAreOnlyIdentifiersAndDates() {
        assertThat(ProcessVariables.ALLOWED).allSatisfy(name -> assertThat(name.toLowerCase())
                .doesNotContain("skarzacy", "nazwisko", "pesel", "adres", "pelnomocnik", "imie", "email", "telefon"));
    }

    @Test
    void rejectsUnknownVariableNames() {
        assertThatThrownBy(() -> ProcessVariables.validate(Map.of("skarzacyNazwisko", "Przykładowy")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("D8");
    }

    @Test
    void rejectsComplexValueTypes() {
        assertThatThrownBy(() -> ProcessVariables.validate(Map.of(ProcessVariables.SYGNATURA, List.of("Ts 312/26"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsStartVariables() {
        Map<String, Object> v = ProcessVariables.start(42L, "Ts 312/26", ProcessVariables.RodzajPisma.SKARGA);
        assertThat(v).containsEntry(ProcessVariables.CASE_ID, 42L).containsEntry(ProcessVariables.SYGNATURA, "Ts 312/26")
                .containsEntry(ProcessVariables.RODZAJ_PISMA, "SKARGA");
        assertThat(ProcessVariables.deadline(ProcessVariables.TERMIN_BRAKOW, new Date())).containsKey(ProcessVariables.TERMIN_BRAKOW);
    }
}
