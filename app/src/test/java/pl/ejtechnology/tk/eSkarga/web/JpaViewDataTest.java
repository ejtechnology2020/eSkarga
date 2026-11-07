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
        FormalRequirementsService req = Wiring.inject(new FormalRequirementsService(), engine.getDecisionService(), s.requirements);
        return Wiring.inject(new JpaViewData(), s.cases, s.incoming, s.deliveries, s.appeals, s.generalLetters, s.access, s.documents, s.judges, board, s.reports,
                s.archive, s.delegations, s.templates, s.dictionaries, s.holidays, s.caseAccess, s.audit, req, new TestUser(login, roles), clock, new Fmt(), s.search, s.checklist, s.letters, s.notes, s.directory, s.requirements);
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
            assertThat(prezes.notifications(java.time.Instant.EPOCH)).as("Prezes: sprawa do wyznaczenia i nowe zadanie grupy w powiadomieniach")
                    .anySatisfy(n -> assertThat(n.klucz()).startsWith("wyznaczenie:")).anySatisfy(n -> {
                        assertThat(n.klucz()).startsWith("zadanie:");
                        assertThat(n.url()).startsWith(Screen.S17.path());
                    });
            assertThat(prezes.notifications(java.time.Instant.now().plusSeconds(3600))).as("zadanie sprzed „od” nie jest nowe")
                    .noneSatisfy(n -> assertThat(n.klucz()).startsWith("zadanie:"));

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

    /** FR-24, S-14 zakładka „Notatki wewnętrzne”: zespół sędziego (sędzia, asystent, referent sprawy z rolą zespołu) widzi i dodaje notatki; referent – nie. */
    @Test
    void notatkiWewnetrzneWidocznePrzezViewDataTylkoDlaZespolu() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        String sedzia = "sedzia.fikcyjny";
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            s.em.persist(new pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment(loaded, sedzia, pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole.JUDGE, TODAY));
            s.directory.touch(sedzia, "sędzia TK Piotr Fikcyjny", Set.of(AppRole.TK_SEDZIA));
            s.notes.addNote(cf.getId(), "Uwagi do skargi", "Brak wskazania wzorca kontroli.", sedzia, Set.of(AppRole.TK_SEDZIA));
            s.em.persist(new pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment(loaded, "asystent.testowy", pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole.TEAM, TODAY));
            s.notes.addNote(cf.getId(), "Pytanie asystenta", "Czy wzywać o pełnomocnictwo?", "asystent.testowy", Set.of(AppRole.TK_ASYSTENT));
        });
        run(s -> {
            String syg = cf.getDocket().format();
            JpaViewData judge = view(s, sedzia, EnumSet.of(AppRole.TK_SEDZIA));
            assertThat(judge.canReadNotes(syg)).isTrue();
            assertThat(judge.notes(syg)).hasSize(2).first().satisfies(n -> assertThat(n.tytul()).as("najnowsza pierwsza").isEqualTo("Pytanie asystenta"));
            assertThat(judge.notes(syg)).last().satisfies(n -> {
                assertThat(n.tytul()).isEqualTo("Uwagi do skargi");
                assertThat(n.tresc()).isEqualTo("Brak wskazania wzorca kontroli.");
                assertThat(n.autor()).as("autor po nazwie z rejestru S-22").isEqualTo("sędzia TK Piotr Fikcyjny");
                assertThat(n.data()).isNotNull();
            });
            assertThat(judge.notifications(java.time.Instant.EPOCH)).as("sędzia: notatka asystenta tak, własna nie")
                    .anySatisfy(n -> {
                        assertThat(n.klucz()).startsWith("notatka:");
                        assertThat(n.tytul()).isEqualTo("Pytanie asystenta");
                    }).noneSatisfy(n -> assertThat(n.tytul()).isEqualTo("Uwagi do skargi"));
            JpaViewData referent = view(s, REFERENT, EnumSet.of(AppRole.TK_REFERENT));
            assertThat(referent.caseBy(syg)).as("referent sprawy czyta akta").isNotNull();
            assertThat(referent.notifications(java.time.Instant.EPOCH)).noneSatisfy(n -> assertThat(n.klucz()).startsWith("notatka:"));
            assertThat(referent.canReadNotes(syg)).as("referent nie należy do zespołu sędziego").isFalse();
            assertThat(referent.notes(syg)).isEmpty();
        });
    }
}
