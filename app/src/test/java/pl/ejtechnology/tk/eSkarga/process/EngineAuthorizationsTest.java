package pl.ejtechnology.tk.eSkarga.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;

/** Nadania silnika dla grupy aplikacji przy włączonej autoryzacji (T-004, D9): idempotentne, pozwalają użytkownikowi z grupą startować proces. */
class EngineAuthorizationsTest {

    static ProcessEngine engine;

    @BeforeAll
    static void start() {
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:autoryzacje;DB_CLOSE_DELAY=1000");
        cfg.setAuthorizationEnabled(true);
        cfg.setJobExecutorActivate(false);
        engine = cfg.buildProcessEngine();
    }

    @AfterAll
    static void stop() {
        engine.close();
    }

    @Test
    void nadaniaDlaGrupyAplikacjiSaIdempotentneIWystarczajaDoStartuProcesu() {
        assertThat(EngineAuthorizations.ensure(engine)).isEqualTo(EngineAuthorizations.RESOURCES.size());
        assertThat(EngineAuthorizations.ensure(engine)).isZero();
        assertThat(engine.getAuthorizationService().createAuthorizationQuery().groupIdIn(EngineAuthorizations.APP_GROUP).count())
                .isEqualTo(EngineAuthorizations.RESOURCES.size());
        engine.getRepositoryService().createDeployment().addClasspathResource("bpmn/wstepna-kontrola.bpmn").deploy();
        engine.getIdentityService().setAuthentication("admin.wszystko", List.of("tk-referenci", EngineAuthorizations.APP_GROUP));
        try {
            assertThat(engine.getAuthorizationService().isUserAuthorized("admin.wszystko", List.of(EngineAuthorizations.APP_GROUP), Permissions.CREATE, Resources.PROCESS_INSTANCE)).isTrue();
            assertThat(engine.getAuthorizationService().isUserAuthorized("ktos", List.of(), Permissions.CREATE, Resources.PROCESS_INSTANCE)).isFalse();
            // wyliczenie tabeli decyzyjnej (wymogi formalne w aktach) – CREATE_INSTANCE na definicji decyzji
            assertThat(engine.getAuthorizationService().isUserAuthorized("admin.wszystko", List.of(EngineAuthorizations.APP_GROUP), Permissions.CREATE_INSTANCE, Resources.DECISION_DEFINITION)).isTrue();
        } finally {
            engine.getIdentityService().clearAuthentication();
        }
    }
}
