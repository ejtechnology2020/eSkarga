package pl.ejtechnology.tk.eSkarga.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.ExecutionListener;

/** Zaślepka nasłuchu {@code transferCompletedListener} w testach bez CDI: zapamiętuje zmienne z chwili zakończenia przekazania. */
public class RecordingTransferListener implements ExecutionListener {

    public static final List<Map<String, Object>> CALLS = new ArrayList<>();

    @Override
    public void notify(DelegateExecution execution) {
        CALLS.add(Map.of(ProcessVariables.CASE_ID, execution.getVariable(ProcessVariables.CASE_ID),
                ProcessVariables.SYGNATURA_POWIAZANA, String.valueOf(execution.getVariable(ProcessVariables.SYGNATURA_POWIAZANA))));
    }
}
