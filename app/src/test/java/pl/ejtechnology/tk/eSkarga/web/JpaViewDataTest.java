package pl.ejtechnology.tk.eSkarga.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.process.FormalRequirementsService;
import pl.ejtechnology.tk.eSkarga.process.TaskBoardService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.RecordPage;
import pl.ejtechnology.tk.eSkarga.web.model.TaskRow;

/** ADR 0017: mapowanie encji i zadań procesu na rekordy widoków; filtr dostępu (D4) w danych ekranów. */
class JpaViewDataTest extends ServiceTestSupport {

    /** Użytkownik testowy bez kontenera (SecurityContext). */
    static final class TestUser extends CurrentUser {
        private final String login;
        private final Set<AppRole> roles;

        TestUser(String login, Set<AppRole> roles) {
            this.login = login;
            this.roles = roles;
        }

        @Override
        public String getLogin() { return login; }

        @Override
        public Set<AppRole> getRoles() { return roles; }

        @Override
        public AppRole getRole() { return roles.iterator().next(); }

        @Override
        public String getDisplayName() { return login; }
    }

    private JpaViewData view(Services s, String login, Set<AppRole> roles) {
        TaskBoardService board = Wiring.inject(new TaskBoardService(), engine.getTaskService(), s.cases, s.deliveries, s.caseAccess, clock);
        FormalRequirementsService req = Wiring.inject(new FormalRequirementsService(), engine.getDecisionService());
        return Wiring.inject(new JpaViewData(), s.cases, s.incoming, s.deliveries, s.appeals, s.generalLetters, s.access, s.documents, s.judges, board, s.reports,
                s.archive, s.delegations, s.templates, s.dictionaries, s.holidays, s.caseAccess, s.audit, req, new TestUser(login, roles), clock, new Fmt(), s.search, s.checklist, s.letters);
    }

    @Test
    void sprawaZadaniaIAktaWidoczneWedlugRoli() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        engine.getRepositoryService().createDeployment().addClasspathResource("bpmn/wymogi-formalne.dmn").deploy();
        run(s -> {
            JpaViewData referent = view(s, REFERENT, EnumSet.of(AppRole.TK_REFERENT));
            CaseSummary cs = referent.caseBy(cf.getDocket().format());
            assertThat(cs).isNotNull();
            assertThat(cs.status()).isEqualTo(CaseStatus.ZAREJESTROWANA);
            assertThat(cs.skarzacy()).isEqualTo("Jan Przykładowy");
            assertThat(cs.pelnomocnik()).isEqualTo("adw. Anna Testowa");
            assertThat(cs.wersja()).isEqualTo("v1");
            assertThat(referent.cases()).extracting(CaseSummary::sygnatura).contains(cf.getDocket());
            assertThat(referent.searchCases("Przykładowy")).extracting(CaseSummary::sygnatura).contains(cf.getDocket());
            assertThat(referent.incomingLetters()).isNotEmpty();
            assertThat(referent.recordPages(cf.getDocket().format())).extracting(RecordPage::karty).contains("k. 1–14");
            assertThat(referent.checklist(cf.getDocket().format())).hasSize(8).allSatisfy(c -> assertThat(c.podstawa()).startsWith("art."));
            assertThat(referent.processSteps(cf.getDocket().format())).hasSize(6).anySatisfy(p -> assertThat(p.current()).isTrue());
            assertThat(referent.kpisFor(AppRole.TK_REFERENT)).hasSize(4);
            assertThat(referent.audit()).isNotEmpty();
            assertThat(referent.calendar(2026)).hasSize(12);
            assertThat(referent.timeline(cf.getDocket().format())).isNotEmpty();

            JpaViewData prezes = view(s, PREZES, EnumSet.of(AppRole.TK_PREZES));
            List<TaskRow> tasks = prezes.tasksFor(AppRole.TK_PREZES);
            assertThat(tasks).anySatisfy(t -> {
                assertThat(t.sprawa().sygnatura()).isEqualTo(cf.getDocket());
                assertThat(t.akcja()).isEqualTo("Wyznacz sędziego");
                assertThat(t.akcjaUrl()).startsWith(Screen.S17.path());
                assertThat(t.grupowe()).isTrue();
            });
            assertThat(prezes.menuCounter(Screen.S01, AppRole.TK_PREZES)).isGreaterThanOrEqualTo(1);

            JpaViewData asystent = view(s, "asystent.obcy", EnumSet.of(AppRole.TK_ASYSTENT));
            assertThat(asystent.caseBy(cf.getDocket().format())).as("asystent spoza zespołu nie widzi sprawy (D4)").isNull();
            assertThat(asystent.recordPages(cf.getDocket().format())).isEmpty();
            assertThat(asystent.tasksFor(AppRole.TK_ASYSTENT)).isEmpty();
        });
        run(s -> {
            s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            JpaViewData admin = view(s, "admin", EnumSet.of(AppRole.TK_ADMIN));
            assertThat(admin.delegations()).anySatisfy(d -> {
                assertThat(d.osoba()).isEqualTo(PREZES);
                assertThat(d.status()).isEqualTo("ważne");
            });
            assertThat(admin.caseBy(cf.getDocket().format())).as("administrator techniczny nie czyta akt").isNull();
        });
    }
}
