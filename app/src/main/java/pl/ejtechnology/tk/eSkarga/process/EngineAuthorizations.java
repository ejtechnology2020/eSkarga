package pl.ejtechnology.tk.eSkarga.process;

import java.util.List;
import java.util.logging.Logger;

import org.operaton.bpm.engine.AuthorizationService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.authorization.Resources;

/**
 * Uprawnienia silnika Operaton dla aplikacji (D9, T-004). Silnik ma włączoną autoryzację (Cockpit, REST), a tożsamość użytkownika
 * ID-users jest ustawiana na czas żądania ({@code OperatonAuthenticationFilter}); bez nadań silnik odrzucał start procesu
 * („does not have 'CREATE' permission on resource 'ProcessInstance'”). Dostęp do spraw egzekwuje domena eSkargi (D4,
 * {@code CaseAccessPolicy}), więc każdy zalogowany użytkownik eSkargi (grupa {@value #APP_GROUP}) dostaje w silniku pełne
 * uprawnienia do definicji i instancji procesów, zadań oraz ich historii; nadania są idempotentne (po wdrożeniu aplikacji).
 */
public final class EngineAuthorizations {

    private static final Logger LOG = Logger.getLogger(EngineAuthorizations.class.getName());
    /** Grupa silnika nadawana każdemu uwierzytelnionemu użytkownikowi eSkargi (obok grup kandydatów zadań). */
    public static final String APP_GROUP = "tk-eskarga";
    static final List<Resource> RESOURCES = List.of(Resources.PROCESS_DEFINITION, Resources.PROCESS_INSTANCE, Resources.TASK,
            Resources.DEPLOYMENT, Resources.HISTORIC_PROCESS_INSTANCE, Resources.HISTORIC_TASK, Resources.FILTER);

    private EngineAuthorizations() {
    }

    /** Zapewnia nadania dla grupy aplikacji; nic nie robi, gdy autoryzacja silnika jest wyłączona. Zwraca liczbę utworzonych nadań. */
    public static int ensure(ProcessEngine engine) {
        if (!engine.getProcessEngineConfiguration().isAuthorizationEnabled()) {
            return 0;
        }
        AuthorizationService auth = engine.getAuthorizationService();
        int created = 0;
        for (Resource r : RESOURCES) {
            boolean exists = auth.createAuthorizationQuery().groupIdIn(APP_GROUP).resourceType(r).resourceId(Authorization.ANY)
                    .authorizationType(Authorization.AUTH_TYPE_GRANT).count() > 0;
            if (exists) {
                continue;
            }
            Authorization a = auth.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
            a.setGroupId(APP_GROUP);
            a.setResource(r);
            a.setResourceId(Authorization.ANY);
            a.addPermission(Permissions.ALL);
            auth.saveAuthorization(a);
            created++;
        }
        if (created > 0) {
            LOG.info(() -> "Operaton: nadania dla grupy " + APP_GROUP + " – " + RESOURCES.size() + " zasobów");
        }
        return created;
    }
}
