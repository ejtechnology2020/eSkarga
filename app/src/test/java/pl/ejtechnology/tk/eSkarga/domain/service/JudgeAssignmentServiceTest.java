package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Delegation;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-05, BR-10, FR-14, FR-36: wyznaczenie sędziego i upoważnienia (T-016, T-023). */
class JudgeAssignmentServiceTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.fikcyjny";
    private static final String WICEPREZES = "wiceprezes.testowy";

    private CaseFile newCase() {
        long letterId = tx(s -> registerLetter(s)).getId();
        return tx(s -> createCase(s, letterId));
    }

    @Test
    void bezUpowaznieniaOdmowaIZdarzenieAudytu() {
        CaseFile cf = newCase();
        long before = auditCount("odmowa: brak ważnego upoważnienia");
        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), SEDZIA, WICEPREZES, Set.of(AppRole.TK_SEDZIA))))
                .isInstanceOf(SecurityException.class);
        assertThat(auditCount("odmowa: brak ważnego upoważnienia")).isEqualTo(before + 1);
        tx(s -> assertThat(s.cases.require(cf.getId()).current().getStatus()).isEqualTo(CaseStatus.ZAREJESTROWANA));
    }

    @Test
    void upowaznienieObowiazujeTylkoWSwoimOkresie() {
        CaseFile cf = newCase();
        tx(s -> s.delegations.add(WICEPREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Zarządzenie 7/2026", "admin"));
        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), SEDZIA, WICEPREZES, Set.of(AppRole.TK_SEDZIA))))
                .isInstanceOf(SecurityException.class);

        Delegation d = tx(s -> s.delegations.add(WICEPREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "Zarządzenie 8/2026", "admin"));
        ProceduralAct act = tx(s -> s.judges.assign(cf.getId(), "sedzia.drugi", WICEPREZES, Set.of(AppRole.TK_SEDZIA)));
        assertThat(act.getSubtype()).isEqualTo("WYZNACZENIE_SEDZIEGO");

        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.current().getStatus()).isEqualTo(CaseStatus.WE_WSTEPNEJ_KONTROLI);
            assertThat(loaded.current().getJudgeUserId()).isEqualTo("sedzia.drugi");
            assertThat(s.cases.documents(loaded)).hasSize(2);
            assertThat(s.judges.loads()).filteredOn(l -> l.judgeUserId().equals("sedzia.drugi")).singleElement()
                    .satisfies(l -> assertThat(l.casesInReview()).isEqualTo(1));
            assertThat(s.judges.awaiting()).extracting(CaseFile::getId).doesNotContain(cf.getId());
        });
        Task kontrola = engine.getTaskService().createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult();
        assertThat(kontrola.getTaskDefinitionKey()).isEqualTo("T_Kontrola");
        assertThat(kontrola.getAssignee()).isEqualTo("sedzia.drugi");

        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), "inny", WICEPREZES, Set.of(AppRole.TK_SEDZIA))))
                .isInstanceOf(IllegalStateException.class);

        run(s -> s.delegations.revoke(d.getId(), "admin"));
        CaseFile next = newCase();
        assertThatThrownBy(() -> run(s -> s.judges.assign(next.getId(), SEDZIA, WICEPREZES, Set.of(AppRole.TK_SEDZIA))))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void prezesBezUpowaznieniaTakzePotrzebujeWpisuWRejestrze() {
        // BR-10: także Prezes TK działa na podstawie wpisu w rejestrze funkcji (jeden mechanizm weryfikacji)
        CaseFile cf = newCase();
        assertThatThrownBy(() -> run(s -> s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES))))
                .isInstanceOf(SecurityException.class);
        tx(s -> s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie Prezydenta RP (fikcyjne)", "admin"));
        tx(s -> s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES)));
    }

    @Test
    void nakladajaceSieIWygasajaceUpowaznienia() {
        Delegation a = tx(s -> s.delegations.add("u1", "SEKRETARZ", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), "Z 1", "admin"));
        Delegation b = tx(s -> s.delegations.add("u2", "SEKRETARZ", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20), "Z 2", "admin"));
        run(s -> {
            assertThat(s.delegations.overlapping(a)).extracting(Delegation::getId).containsExactly(b.getId());
            assertThat(s.delegations.expiringWithin(10)).extracting(Delegation::getId).contains(a.getId()).doesNotContain(b.getId());
        });
        assertThatThrownBy(() -> tx(s -> s.delegations.add("u3", "SEKRETARZ", TODAY, TODAY, "", "admin"))).isInstanceOf(IllegalArgumentException.class);
    }
}
