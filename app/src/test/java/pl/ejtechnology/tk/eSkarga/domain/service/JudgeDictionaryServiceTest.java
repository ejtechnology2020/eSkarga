package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.JudgeFunction;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Judge;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** ADR 0027: słownik sędziów (login, funkcja, kadencja) jako jedyne źródło sędziów do wyznaczenia (UC-05) i składów (UC-11). */
class JudgeDictionaryServiceTest extends ServiceTestSupport {

    @Test
    void wpisWalidacjaKadencjaIWycofanie() {
        Judge j = tx(s -> s.judgeDictionary.add("sedzia.nowy", "Anna Kadencyjna", JudgeFunction.SEDZIA, LocalDate.of(2026, 12, 1), LocalDate.of(2035, 11, 30), "admin"));
        assertThat(j.titledName()).isEqualTo("sędzia TK Anna Kadencyjna");
        assertThat(j.eligibleOn(TODAY)).as("kadencja zaczyna się po TODAY (03.11.2026)").isFalse();
        assertThat(j.eligibleOn(LocalDate.of(2026, 12, 1))).isTrue();
        assertThatThrownBy(() -> tx(s -> s.judgeDictionary.add("sedzia.nowy", "Dubel", JudgeFunction.SEDZIA, TODAY, null, "admin"))).hasMessageContaining("już w słowniku");
        assertThatThrownBy(() -> tx(s -> s.judgeDictionary.add("x y", "Zły login", JudgeFunction.SEDZIA, TODAY, null, "admin"))).hasMessageContaining("Login");
        assertThatThrownBy(() -> tx(s -> s.judgeDictionary.add("sedzia.z", "Z", JudgeFunction.SEDZIA, TODAY, TODAY.minusDays(1), "admin"))).hasMessageContaining("Koniec kadencji");
        assertThatThrownBy(() -> tx(s -> s.judgeDictionary.add("sedzia.z", " ", JudgeFunction.SEDZIA, TODAY, null, "admin"))).hasMessageContaining("Imię i nazwisko");

        tx(s -> s.judgeDictionary.update(j.getId(), "Anna Kadencyjna", JudgeFunction.WICEPREZES, LocalDate.of(2026, 1, 1), LocalDate.of(2034, 12, 31), "admin"));
        run(s -> {
            Judge u = s.judgeDictionary.require(j.getId());
            assertThat(u.getFunction()).isEqualTo(JudgeFunction.WICEPREZES);
            assertThat(u.titledName()).isEqualTo("Wiceprezes TK Anna Kadencyjna");
            assertThat(u.eligibleOn(TODAY)).isTrue();
            assertThat(s.judgeDictionary.eligibleOn(TODAY)).extracting(Judge::getUserId).contains("sedzia.nowy");
            assertThat(s.judgeDictionary.displayName("sedzia.nowy")).isEqualTo("Wiceprezes TK Anna Kadencyjna");
            assertThat(s.judgeDictionary.displayName("nieznany")).isEqualTo("nieznany");
        });
        tx(s -> s.judgeDictionary.setActive(j.getId(), false, "admin"));
        run(s -> {
            assertThat(s.judgeDictionary.eligibleOn(TODAY)).extracting(Judge::getUserId).doesNotContain("sedzia.nowy");
            assertThat(s.judgeDictionary.all()).extracting(Judge::getUserId).contains("sedzia.nowy");
            assertThatThrownBy(() -> s.judgeDictionary.requireEligible("sedzia.nowy", TODAY)).hasMessageContaining("wycofany");
            assertThat(s.audit.recent(30)).extracting(e -> e.getAction()).anySatisfy(a -> assertThat(a).contains("wycofanie sędziego w słowniku: sedzia.nowy"));
        });
    }

    @Test
    void wyznaczenieISkladTylkoZeSlownikaWKadencji() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judgeDictionary.add("sedzia.emeryt", "Jan Emerytowany", JudgeFunction.SEDZIA, LocalDate.of(2010, 1, 1), LocalDate.of(2019, 12, 31), "admin");
        });
        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), "spoza.slownika", PREZES, Set.of(AppRole.TK_PREZES))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nie jest w słowniku");
        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), "sedzia.emeryt", PREZES, Set.of(AppRole.TK_PREZES))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("poza kadencją");
        var act = tx(s -> s.judges.assign(cf.getId(), "sedzia.fikcyjny", null, PREZES, Set.of(AppRole.TK_PREZES)));
        assertThat(act.getPanelUserIds()).isEqualTo("sedzia.fikcyjny");
        run(s -> assertThat(new String(store.files().get(s.cases.documents(s.cases.require(cf.getId())).get(1).getStorageKey()).bytes(), java.nio.charset.StandardCharsets.UTF_8))
                .as("nazwa sędziego do zarządzenia ze słownika").contains("sędzia TK sedzia.fikcyjny"));

        // skład (UC-11) sprawdza DeliveryAndAppealServiceTest – zażalenie wymaga pełnej ścieżki procesu (odmowa, doręczenie, termin)
    }
}
