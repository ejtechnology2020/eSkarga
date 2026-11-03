package pl.ejtechnology.tk.eSkarga.process;

import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.externaltask.LockedExternalTask;
import org.operaton.bpm.engine.impl.util.ClockUtil;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.test.Deployment;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Testy procesowe modelu „Wstępna kontrola Ts/Tw” (T-021): każda ścieżka ma test, a razem pokrywają wszystkie przejścia.
 * Timery dostają gotową datę z domeny ({@code terminBrakow}, {@code terminZazalenia}) zamiast stałego P7D (D6).
 */
@Deployment(resources = "bpmn/wstepna-kontrola.bpmn")
class WstepnaKontrolaProcessTest {

    @RegisterExtension
    static ProcessEngineExtension extension = ProcessEngineExtension.builder().build();

    private static final String KEY = PreliminaryReviewProcess.PROCESS_KEY;
    private static final DocketNumber SYG = DocketNumber.parse("Ts 312/26");

    private RuntimeService runtime;
    private TaskService tasks;
    private ManagementService management;
    private PreliminaryReviewProcess process;

    @BeforeEach
    void setUp() {
        runtime = extension.getRuntimeService();
        tasks = extension.getTaskService();
        management = extension.getManagementService();
        process = PreliminaryReviewProcess.forTests(runtime, management, tasks);
        ClockUtil.setCurrentTime(Date.from(LocalDate.of(2026, 10, 14).atStartOfDay(AppClock.WARSAW).toInstant()));
    }

    @AfterEach
    void tearDown() {
        ClockUtil.reset();
    }

    private ProcessInstance startSkarga() {
        ProcessInstance pi = runtime.startProcessInstanceByKey(KEY, SYG.format(), ProcessVariables.start(312L, SYG.format(), ProcessVariables.RodzajPisma.SKARGA));
        assertThat(pi).isWaitingAt("T_Wplyw").task().hasCandidateGroup("tk-biuro-podawcze");
        complete(task(pi));
        assertThat(pi).isWaitingAt("T_Wpis").task().hasCandidateGroup("tk-referenci");
        complete(task(pi));
        assertThat(pi).isWaitingAt("T_Wyznacz").task().hasCandidateGroup("tk-prezes");
        complete(task(pi), ProcessVariables.judgeAssigned("pfikcyjny"));
        assertThat(pi).isWaitingAt("T_Kontrola").task().isAssignedTo("pfikcyjny");
        return pi;
    }

    private void przekazanieZewnetrzne(ProcessInstance pi) {
        assertThat(pi).isWaitingAt("T_Przekaz");
        List<LockedExternalTask> locked = extension.getExternalTaskService().fetchAndLock(1, "test-worker")
                .topic("przekazanie-merytoryczne", 60_000).execute();
        Assertions.assertThat(locked).hasSize(1);
        Assertions.assertThat(locked.get(0).getBusinessKey()).isEqualTo(SYG.format());
        extension.getExternalTaskService().complete(locked.get(0).getId(), "test-worker");
        assertThat(pi).isEnded().hasPassed("E_Przekaz");
    }

    private void ustawTermin(ProcessInstance pi, String variable, LocalDate end) {
        process.setDeadline(SYG, variable, end);
        Job timer = management.createJobQuery().processInstanceId(pi.getId()).timers().singleResult();
        Assertions.assertThat(timer).isNotNull();
        Assertions.assertThat(timer.getDuedate()).isEqualTo(Date.from(end.plusDays(1).atStartOfDay(AppClock.WARSAW).toInstant()));
    }

    private void odpalTimer(ProcessInstance pi) {
        Job timer = management.createJobQuery().processInstanceId(pi.getId()).timers().singleResult();
        ClockUtil.setCurrentTime(new Date(timer.getDuedate().getTime() + 1000));
        management.executeJob(timer.getId());
    }

    @Test
    void pismoOgolne() {
        ProcessInstance pi = runtime.startProcessInstanceByKey(KEY, "DW 2026/04808", ProcessVariables.start(4808L, "DW 2026/04808", ProcessVariables.RodzajPisma.OGOLNE));
        complete(task(pi));
        assertThat(pi).isWaitingAt("T_Ogolne");
        complete(task(pi));
        assertThat(pi).isEnded().hasPassedInOrder("Start", "T_Wplyw", "G_Typ", "T_Ogolne", "E_Ogolne");
    }

    @Test
    void nadanieBieguOdRazu() {
        ProcessInstance pi = startSkarga();
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.NADANIE_BIEGU));
        assertThat(pi).isWaitingAt("T_Nadanie").task().isAssignedTo("pfikcyjny");
        complete(task(pi));
        assertThat(pi).hasPassed("G_Wynik", "T_Nadanie", "G_Bieg");
        przekazanieZewnetrzne(pi);
    }

    @Test
    void brakiUzupelnione() {
        ProcessInstance pi = startSkarga();
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.BRAKI));
        assertThat(pi).isWaitingAt("T_Braki");
        // domena ustawia wstępny termin (wysyłka + okres oczekiwania); timer powstaje przy bramce zdarzeniowej
        complete(task(pi), ProcessVariables.deadline(ProcessVariables.TERMIN_BRAKOW, date(2026, 11, 20)));
        assertThat(pi).isWaitingAt("EG_Braki");
        // zarejestrowano doręczenie 02.11 → termin 09.11 (korekta przestawia timer)
        ustawTermin(pi, ProcessVariables.TERMIN_BRAKOW, LocalDate.of(2026, 11, 9));
        process.supplementReceived(SYG);
        assertThat(pi).isWaitingAt("T_Kontrola").hasPassed("T_Braki", "EG_Braki", "M_Uzup", "G_Powrot", "G_Merge");
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.NADANIE_BIEGU));
        complete(task(pi));
        przekazanieZewnetrzne(pi);
    }

    @Test
    void brakiNieuzupelnioneProwadzaDoOdmowyPrawomocnej() {
        ProcessInstance pi = startSkarga();
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.BRAKI));
        complete(task(pi), ProcessVariables.deadline(ProcessVariables.TERMIN_BRAKOW, date(2026, 11, 9)));
        odpalTimer(pi);
        assertThat(pi).isWaitingAt("T_Kontrola").hasPassed("TM_Braki", "G_Powrot", "G_Merge");
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.ODMOWA));
        assertThat(pi).isWaitingAt("T_Odmowa");
        complete(task(pi), ProcessVariables.deadline(ProcessVariables.TERMIN_ZAZALENIA, date(2026, 11, 30)));
        assertThat(pi).isWaitingAt("EG_Zaz");
        ustawTermin(pi, ProcessVariables.TERMIN_ZAZALENIA, LocalDate.of(2026, 12, 4));
        odpalTimer(pi); // odmowa bez zażalenia
        assertThat(pi).isEnded().hasPassed("T_Odmowa", "EG_Zaz", "TM_Zaz", "E_Odmowa");
    }

    @Test
    void zazalenieSpoznione() {
        ProcessInstance pi = doOdmowy();
        process.appealReceived(SYG, false);
        assertThat(pi).isWaitingAt("T_BezRozp").task().hasCandidateGroup("tk-sklad");
        complete(task(pi));
        assertThat(pi).isEnded().hasPassed("M_Zaz", "G_Termin", "T_BezRozp", "E_BezRozp");
    }

    @Test
    void zazalenieNieuwzglednione() {
        ProcessInstance pi = doOdmowy();
        process.appealReceived(SYG, true);
        assertThat(pi).isWaitingAt("T_Skieruj").task().hasCandidateGroup("tk-prezes");
        complete(task(pi));
        assertThat(pi).isWaitingAt("T_RozpZaz").task().hasCandidateGroup("tk-sklad");
        complete(task(pi), ProcessVariables.appealOutcome(false));
        assertThat(pi).isEnded().hasPassed("G_Termin", "T_Skieruj", "T_RozpZaz", "G_Zasadne", "E_Nieuwzgl");
    }

    @Test
    void zazalenieUwzglednioneNadajeBieg() {
        ProcessInstance pi = doOdmowy();
        process.appealReceived(SYG, true);
        complete(task(pi));
        complete(task(pi), ProcessVariables.appealOutcome(true));
        assertThat(pi).hasPassed("G_Zasadne", "G_Bieg");
        przekazanieZewnetrzne(pi);
    }

    @Test
    void zmienneProcesuZawierajaTylkoIdentyfikatory() {
        ProcessInstance pi = startSkarga();
        Map<String, Object> vars = runtime.getVariables(pi.getId());
        Assertions.assertThat(vars.keySet()).allMatch(ProcessVariables.ALLOWED::contains);
    }

    private ProcessInstance doOdmowy() {
        ProcessInstance pi = startSkarga();
        complete(task(pi), ProcessVariables.reviewResult(ProcessVariables.WynikKontroli.ODMOWA));
        complete(task(pi), ProcessVariables.deadline(ProcessVariables.TERMIN_ZAZALENIA, date(2026, 11, 4)));
        assertThat(pi).isWaitingAt("EG_Zaz");
        return pi;
    }

    private static Date date(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).plusDays(1).atStartOfDay(AppClock.WARSAW).toInstant());
    }
}
