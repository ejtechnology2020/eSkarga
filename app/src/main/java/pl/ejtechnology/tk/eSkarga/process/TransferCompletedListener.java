package pl.ejtechnology.tk.eSkarga.process;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.ExecutionListener;

import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;

/**
 * Koniec external taska „Przekaż sprawę do repertorium SK / K / U” (UC-12, FR-19). Worker nadaje sygnaturę powiązaną
 * w zmiennej {@code sygnaturaPowiazana}; ten nasłuch – w tej samej transakcji silnika (D1, D9) – zamyka sprawę Ts/Tw
 * nową wersją wpisu „zakończona – nadano bieg” i blokuje edycję (BR-08).
 */
@Named("transferCompletedListener")
@Dependent
public class TransferCompletedListener implements ExecutionListener {

    @Inject
    CaseFileService cases;

    @Override
    public void notify(DelegateExecution execution) {
        Object caseId = execution.getVariable(ProcessVariables.CASE_ID);
        Object related = execution.getVariable(ProcessVariables.SYGNATURA_POWIAZANA);
        if (caseId == null) {
            throw new IllegalStateException("Brak zmiennej caseId w procesie " + execution.getProcessBusinessKey());
        }
        cases.markTransferred(((Number) caseId).longValue(), related == null ? null : related.toString(), "system:przekazanie");
    }
}
