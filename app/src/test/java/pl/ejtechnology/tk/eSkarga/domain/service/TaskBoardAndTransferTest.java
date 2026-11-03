package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.externaltask.LockedExternalTask;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.process.RecordingTransferListener;
import pl.ejtechnology.tk.eSkarga.process.TaskBoardService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

/** UC-18 (pulpit zadań na TaskService, T-022) i UC-12 (przekazanie do SK/K/U zamyka sprawę, T-028). */
class TaskBoardAndTransferTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.fikcyjny";

    private TaskBoardService board(Services s) {
        return Wiring.inject(new TaskBoardService(), engine.getTaskService(), s.cases, s.deliveries, s.caseAccess, clock);
    }

    @Test
    void pulpitPokazujeZadaniaGrupoweIPrzydzieloneWedlugRol() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));

        run(s -> {
            TaskBoardService b = board(s);
            assertThat(TaskBoardService.groupsFor(Set.of(AppRole.TK_PREZES, AppRole.TK_ADMIN))).containsExactly("tk-prezes");
            List<TaskBoardService.Item> prezes = b.tasksFor(PREZES, Set.of(AppRole.TK_PREZES));
            assertThat(prezes).anySatisfy(i -> {
                assertThat(i.taskKey()).isEqualTo("T_Wyznacz");
                assertThat(i.docket()).isEqualTo(cf.getDocket());
                assertThat(i.group()).isTrue();
                assertThat(i.deadlineEnd()).isNull();
            });
            assertThat(b.tasksFor("asystent.obcy", Set.of(AppRole.TK_ASYSTENT))).as("asystent bez przydziału nie widzi zadań").isEmpty();
            assertThat(b.tasksFor(REFERENT, Set.of(AppRole.TK_REFERENT))).extracting(TaskBoardService.Item::taskKey).doesNotContain("T_Wyznacz");
        });

        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES));
        });
        run(s -> {
            List<TaskBoardService.Item> sedzia = board(s).tasksFor(SEDZIA, Set.of(AppRole.TK_SEDZIA));
            assertThat(sedzia).filteredOn(i -> i.docket().equals(cf.getDocket())).singleElement().satisfies(i -> {
                assertThat(i.taskKey()).isEqualTo("T_Kontrola");
                assertThat(i.group()).isFalse();
                assertThat(i.assignee()).isEqualTo(SEDZIA);
                assertThat(i.status()).isEqualTo(CaseStatus.WE_WSTEPNEJ_KONTROLI);
            });
            assertThat(board(s).dueWithin(2, "ktos", Set.of(AppRole.TK_REFERENT))).as("widok zaległości tylko dla kierownika/Prezesa").isEmpty();
        });
    }

    @Test
    void przekazanieDoSkZamykaSpraweIBlokujeEdycje() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES));
        });
        var tasks = engine.getTaskService();
        String piId = engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult().getId();
        tasks.complete(tasks.createTaskQuery().processInstanceId(piId).singleResult().getId(), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.NADANIE_BIEGU));
        tasks.complete(tasks.createTaskQuery().processInstanceId(piId).singleResult().getId());
        List<LockedExternalTask> locked = engine.getExternalTaskService().fetchAndLock(1, "test-worker").topic("przekazanie-merytoryczne", 60_000).execute();
        assertThat(locked).hasSize(1);
        int before = RecordingTransferListener.CALLS.size();
        engine.getExternalTaskService().complete(locked.get(0).getId(), "test-worker", ProcessVariables.transferred("SK 9/26"));
        assertThat(RecordingTransferListener.CALLS).hasSize(before + 1);

        // w aplikacji nasłuch wywołuje CaseFileService.markTransferred w transakcji silnika
        run(s -> s.cases.markTransferred(cf.getId(), "SK 9/26", "system:przekazanie"));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.current().getStatus()).isEqualTo(CaseStatus.ZAKONCZONA_NADANO_BIEG);
            assertThat(loaded.current().getClosingMode()).isEqualTo(ClosingMode.NADANIE_BIEGU);
            assertThat(loaded.current().getRelatedDocket()).isEqualTo("SK 9/26");
            assertThat(loaded.current().isEffective()).isTrue();
            assertThat(loaded.getDocket()).as("sygnatura Ts bez zmian").isEqualTo(cf.getDocket());
            // ponowne wywołanie (ponowienie workera) nie tworzy kolejnej wersji
            int versions = loaded.getVersions().size();
            s.cases.markTransferred(cf.getId(), "SK 9/26", "system:przekazanie");
            assertThat(loaded.getVersions()).hasSize(versions);
        });
    }
}
