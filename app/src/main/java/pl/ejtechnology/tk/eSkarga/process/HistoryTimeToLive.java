package pl.ejtechnology.tk.eSkarga.process;

import java.util.List;

import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.repository.DecisionDefinition;
import org.operaton.bpm.engine.repository.ProcessDefinition;

import pl.ejtechnology.tk.eSkarga.security.OidcSettings;

/**
 * Czas przechowywania historii silnika (T-020) z konfiguracji ({@code eskarga.process.httl.days}, domyślnie 180).
 * Model BPMN ma wartość stałą (Operaton nie przyjmuje wyrażenia w {@code historyTimeToLive}), więc po wdrożeniu
 * aktualizujemy ją na definicjach przez {@link RepositoryService}.
 */
public final class HistoryTimeToLive {

    public static final String PROPERTY = "eskarga.process.httl.days";
    public static final int DEFAULT_DAYS = 180;

    private HistoryTimeToLive() {
    }

    public static int configuredDays() {
        String v = OidcSettings.value(PROPERTY, Integer.toString(DEFAULT_DAYS));
        int days = Integer.parseInt(v.trim());
        if (days <= 0) {
            throw new IllegalArgumentException(PROPERTY + " musi być dodatnie: " + v);
        }
        return days;
    }

    /** Ustawia HTTL na najnowszych wersjach wszystkich definicji procesów i decyzji; zwraca liczbę zaktualizowanych. */
    public static int apply(RepositoryService repository, int days) {
        int updated = 0;
        List<ProcessDefinition> processes = repository.createProcessDefinitionQuery().latestVersion().list();
        for (ProcessDefinition pd : processes) {
            if (pd.getHistoryTimeToLive() == null || pd.getHistoryTimeToLive() != days) {
                repository.updateProcessDefinitionHistoryTimeToLive(pd.getId(), days);
                updated++;
            }
        }
        List<DecisionDefinition> decisions = repository.createDecisionDefinitionQuery().latestVersion().list();
        for (DecisionDefinition dd : decisions) {
            if (dd.getHistoryTimeToLive() == null || dd.getHistoryTimeToLive() != days) {
                repository.updateDecisionDefinitionHistoryTimeToLive(dd.getId(), days);
                updated++;
            }
        }
        return updated;
    }
}
