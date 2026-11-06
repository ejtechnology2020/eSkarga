package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
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

    /** UC-05 dwuetapowo (S-17 → S-16): projekt zarządzenia nie wyznacza; dopiero podpis przydziela sędziego, zmienia status i zamyka zadanie. */
    @Test
    void wyznaczenieStajeSieSkutecznePoPodpisieZarzadzenia() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        long wyznaczenPrzed = auditCount("wyznaczenie sędziego:");   // baza wspólna dla klasy – liczymy przyrost
        run(s -> s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin"));
        var act = tx(s -> s.judges.draftOrder(cf.getId(), SEDZIA, "Ewa Wzorcowa", PREZES, Set.of(AppRole.TK_PREZES)));
        assertThat(act.getStatus()).isEqualTo(pl.ejtechnology.tk.eSkarga.domain.model.ActStatus.PROJEKT);
        assertThat(act.getPanelUserIds()).isEqualTo(SEDZIA);
        run(s -> {
            assertThat(s.cases.require(cf.getId()).current().getStatus()).as("przed podpisem sprawa nadal czeka").isEqualTo(pl.ejtechnology.tk.eSkarga.domain.CaseStatus.ZAREJESTROWANA);
            assertThat(s.judges.awaiting()).extracting(CaseFile::getId).contains(cf.getId());
            assertThat(s.judges.draftOrder(cf.getId(), SEDZIA, "Ewa Wzorcowa", PREZES, Set.of(AppRole.TK_PREZES)).getId()).as("ten sam projekt bez duplikatu").isEqualTo(act.getId());
        });
        long docId = act.getDocument().getId();
        var d = tx(s -> s.signing.begin(docId, List.of("MIIB..."), "RSA_SHA256", PREZES));
        assertThatThrownBy(() -> run(s -> s.signing.complete(docId, d.sessionId(), "c2ln", true, LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO, "ktos.inny")))
                .as("podpisuje osoba, która sporządziła projekt").isInstanceOf(SecurityException.class);
        var d2 = tx(s -> s.signing.begin(docId, List.of("MIIB..."), "RSA_SHA256", PREZES));
        run(s -> s.signing.complete(docId, d2.sessionId(), "c2ln", true, LetterKind.ZARZ_WYZNACZENIE_SEDZIEGO, PREZES));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.current().getStatus()).isEqualTo(pl.ejtechnology.tk.eSkarga.domain.CaseStatus.WE_WSTEPNEJ_KONTROLI);
            assertThat(loaded.current().getJudgeUserId()).isEqualTo(SEDZIA);
            assertThat(s.caseAccess.assignment(loaded).judgeId()).isEqualTo(SEDZIA);
            assertThat(s.judges.pendingOrders(loaded)).isEmpty();
            assertThat(engine.getTaskService().createTaskQuery().processInstanceBusinessKey(loaded.getDocket().format()).singleResult().getTaskDefinitionKey()).isEqualTo("T_Kontrola");
        });
        assertThat(auditCount("wyznaczenie sędziego:") - wyznaczenPrzed).as("skutek wyznaczenia zapisany raz (przy podpisie)").isEqualTo(1);
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
