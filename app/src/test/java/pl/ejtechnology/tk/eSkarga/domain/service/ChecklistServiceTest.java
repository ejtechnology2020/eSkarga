package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.ChecklistState;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-06 (FR-11): ocena wymogów listy kontrolnej – tylko dopisywana, obowiązuje najnowsza; role; audyt. UC-05: zarządzenie z pliku PDF. */
class ChecklistServiceTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.testowy";

    @Test
    void ocenaWymoguJestDopisywanaIObowiazujeNajnowsza() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> s.checklist.record(cf.getId(), "PELNOMOCNICTWO", ChecklistState.BRAK, "brak pełnomocnictwa szczególnego", "k. 23", REFERENT, Set.of(AppRole.TK_REFERENT)));
        run(s -> s.checklist.record(cf.getId(), "PELNOMOCNICTWO", ChecklistState.SPELNIONY, "uzupełnione pismem DW", null, REFERENT, Set.of(AppRole.TK_REFERENT)));
        run(s -> s.checklist.record(cf.getId(), "PRZEPIS", ChecklistState.SPELNIONY, null, "k. 1", REFERENT, Set.of(AppRole.TK_REFERENT, AppRole.TK_ADMIN)));
        run(s -> {
            var oceny = s.checklist.current(s.cases.require(cf.getId()));
            assertThat(oceny).containsOnlyKeys("PELNOMOCNICTWO", "PRZEPIS");
            assertThat(oceny.get("PELNOMOCNICTWO").getState()).isEqualTo(ChecklistState.SPELNIONY);
            assertThat(oceny.get("PELNOMOCNICTWO").getNote()).isEqualTo("uzupełnione pismem DW");
            assertThat(s.em.createQuery("select count(e) from ChecklistEntry e", Long.class).getSingleResult()).as("wiersze tylko dopisywane").isEqualTo(3L);
        });
        assertThat(auditCount("lista kontrolna: PELNOMOCNICTWO")).isEqualTo(2);
    }

    @Test
    void ocenaPozaRolaAlboBezDostepuJestOdrzucana() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        assertThatThrownBy(() -> run(s -> s.checklist.record(cf.getId(), "PRZEPIS", ChecklistState.BRAK, null, null, "bp", Set.of(AppRole.TK_BIURO_PODAWCZE))))
                .isInstanceOf(SecurityException.class);
        // asystent spoza zespołu sędziego nie czyta akt (D4), więc nie ocenia
        assertThatThrownBy(() -> run(s -> s.checklist.record(cf.getId(), "PRZEPIS", ChecklistState.BRAK, null, null, "asystent.obcy", Set.of(AppRole.TK_ASYSTENT))))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void wyznaczenieSedziegoTworzyZarzadzenieZPlikiemZSzablonu() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judges.assign(cf.getId(), SEDZIA, "Ewa Wzorcowa", PREZES, Set.of(AppRole.TK_PREZES));
        });
        run(s -> {
            CaseDocument order = s.cases.documents(s.cases.require(cf.getId())).stream().filter(d -> d.getTitle().contains("wyznaczeniu sędziego")).findFirst().orElseThrow();
            assertThat(order.getStorageKey()).as("zarządzenie ma plik PDF w repozytorium").startsWith("akta/");
            assertThat(order.getSha256()).hasSize(64);
            // zaślepka usługi dokumentów zapisuje kod szablonu i sygnaturę; nazwisko sędziego trafia do modelu szablonu (sedzia)
            assertThat(new String(store.files().get(order.getStorageKey()).bytes(), java.nio.charset.StandardCharsets.UTF_8)).contains("ZARZ_WYZNACZENIE_SEDZIEGO").contains(cf.getDocket().format());
            assertThat(docs.rendered).contains("ZARZ_WYZNACZENIE_SEDZIEGO");
        });
    }
}
