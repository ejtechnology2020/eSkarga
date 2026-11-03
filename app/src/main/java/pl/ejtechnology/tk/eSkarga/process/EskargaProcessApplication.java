package pl.ejtechnology.tk.eSkarga.process;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Local;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;

import org.operaton.bpm.application.PostDeploy;
import org.operaton.bpm.application.ProcessApplication;
import org.operaton.bpm.application.ProcessApplicationInterface;
import org.operaton.bpm.application.impl.JakartaEjbProcessApplication;
import org.operaton.bpm.engine.ProcessEngine;

/**
 * Process application EJB dla współdzielonego silnika Operaton w WildFly 41 (D9), wzorzec z dokumentacji
 * dystrybucji (singleton startowy dziedziczący z {@link JakartaEjbProcessApplication}). Modele z {@code bpmn/}
 * wdraża {@code META-INF/processes.xml}. Zmienne procesu zawierają tylko identyfikatory (D8).
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
@Local(ProcessApplicationInterface.class)
@ProcessApplication("eSkarga – wstępna kontrola Ts/Tw")
public class EskargaProcessApplication extends JakartaEjbProcessApplication {

    @PostConstruct
    @Override
    public void deploy() {
        super.deploy();
    }

    @PreDestroy
    @Override
    public void undeploy() {
        super.undeploy();
    }

    /** HTTL z konfiguracji (T-020); historia na poziomie audit – pełny ślad czynności jest w dzienniku domenowym (NFR-02). */
    @PostDeploy
    public void afterDeploy(ProcessEngine engine) {
        HistoryTimeToLive.apply(engine.getRepositoryService(), HistoryTimeToLive.configuredDays());
    }
}
