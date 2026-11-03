package pl.ejtechnology.tk.eSkarga.process;

import java.time.LocalDate;
import java.util.Date;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Adapter procesu „Wstępna kontrola Ts/Tw” (T-020). Start i korelacja komunikatów działają w transakcji
 * wywołującego ({@code MANDATORY}) razem z wpisem do repertorium (D1). Nigdy nie wołamy REST silnika.
 */
@Stateless
public class PreliminaryReviewProcess {

    public static final String PROCESS_KEY = "Proc_WstepnaKontrola";
    public static final String MSG_UZUPELNIENIE = "Pismo uzupełniające braki";
    public static final String MSG_ZAZALENIE = "Zażalenie na odmowę nadania dalszego biegu";

    @Inject
    private RuntimeService runtimeService;
    @Inject
    private ManagementService managementService;

    /** Kluczem biznesowym jest sygnatura (D8). */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public ProcessInstance start(long caseId, DocketNumber sygnatura, ProcessVariables.RodzajPisma rodzaj) {
        return runtimeService.startProcessInstanceByKey(PROCESS_KEY, sygnatura.format(),
                ProcessVariables.start(caseId, sygnatura.format(), rodzaj));
    }

    /** Pismo uzupełniające braki (UC-09) – komunikat skorelowany po sygnaturze. */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void supplementReceived(DocketNumber sygnatura) {
        runtimeService.createMessageCorrelation(MSG_UZUPELNIENIE).processInstanceBusinessKey(sygnatura.format()).correlate();
    }

    /** Zażalenie (UC-11) – komunikat z pomocniczą oceną terminowości wyliczoną w domenie. */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void appealReceived(DocketNumber sygnatura, boolean inTime) {
        runtimeService.createMessageCorrelation(MSG_ZAZALENIE).processInstanceBusinessKey(sygnatura.format())
                .setVariables(ProcessVariables.appealTimeliness(inTime)).correlate();
    }

    /** Po zarejestrowaniu doręczenia domena ustawia datę timera (D6); korekta doręczenia aktualizuje zmienną. */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void setDeadline(DocketNumber sygnatura, String variable, LocalDate end) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(sygnatura.format()).singleResult();
        if (pi == null) {
            throw new IllegalStateException("Brak procesu dla sprawy " + sygnatura.format());
        }
        Date date = Date.from(end.plusDays(1).atStartOfDay(AppClock.WARSAW).toInstant());
        runtimeService.setVariables(pi.getId(), ProcessVariables.deadline(variable, date));
        // timer przy bramce zdarzeniowej już czeka: korekta doręczenia przestawia jego datę (T-025, T-041)
        for (Job job : managementService.createJobQuery().processInstanceId(pi.getId()).timers().list()) {
            managementService.setJobDuedate(job.getId(), date);
        }
    }

    /** Konstruktor do testów procesowych bez CDI. */
    public static PreliminaryReviewProcess forTests(RuntimeService runtimeService, ManagementService managementService) {
        PreliminaryReviewProcess p = new PreliminaryReviewProcess();
        p.runtimeService = runtimeService;
        p.managementService = managementService;
        return p;
    }
}
