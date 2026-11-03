package pl.ejtechnology.tk.eSkarga.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.impl.util.ClockUtil;
import org.operaton.bpm.engine.test.Deployment;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import pl.ejtechnology.tk.eSkarga.audit.AuditEvent;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.model.PowerOfAttorneyKind;
import pl.ejtechnology.tk.eSkarga.domain.model.Profession;
import pl.ejtechnology.tk.eSkarga.domain.service.AccessRequestService;
import pl.ejtechnology.tk.eSkarga.domain.service.AppealService;
import pl.ejtechnology.tk.eSkarga.domain.service.ArchiveService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.LetterService;
import pl.ejtechnology.tk.eSkarga.domain.service.SigningService;
import pl.ejtechnology.tk.eSkarga.domain.service.CorrectionService;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.domain.service.DeliveryService;
import pl.ejtechnology.tk.eSkarga.domain.service.DictionaryService;
import pl.ejtechnology.tk.eSkarga.domain.service.GeneralLetterService;
import pl.ejtechnology.tk.eSkarga.domain.service.HolidayService;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.domain.service.JudgeAssignmentService;
import pl.ejtechnology.tk.eSkarga.domain.service.NoteService;
import pl.ejtechnology.tk.eSkarga.domain.service.ReportService;
import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;
import pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess;
import pl.ejtechnology.tk.eSkarga.search.InMemorySearchIndex;
import pl.ejtechnology.tk.eSkarga.search.SearchService;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Baza testów serwisów domenowych: Flyway + JPA na H2 (schemat {@code repertorium}) oraz wbudowany silnik Operaton
 * z wdrożonym modelem „Wstępna kontrola”. Każdy blok {@link #tx} działa na świeżym EntityManagerze z serwisami
 * zszytymi przez {@link Wiring}; „dziś” to 03.11.2026 (dane przykładowe, ADR 0004).
 */
@Deployment(resources = "bpmn/wstepna-kontrola.bpmn")
public abstract class ServiceTestSupport {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    public static final String REFERENT = "jtestowy";
    public static final String PREZES = "pfikcyjny";
    public static final String KIEROWNIK = "mprzykladowa";

    @RegisterExtension
    protected static ProcessEngineExtension engine = ProcessEngineExtension.builder().build();

    protected static JpaTestSupport db;
    protected static AppClock clock;
    protected static InMemoryDocumentStore store;
    protected static StubDocsGateway docs;
    protected static InMemorySearchIndex searchIndex;
    /** Walidator szablonów do testów: odrzuca źródło zawierające „<script” albo puste. */
    protected static TemplateService.TemplateValidator validator;

    /** Serwisy zszyte na jednym EntityManagerze – odpowiednik jednej transakcji JTA. */
    public static final class Services {
        public final EntityManager em;
        public final AuditService audit;
        public final DocketNumberService docketNumbers;
        public final PreliminaryReviewProcess process;
        public final IncomingRegister incoming;
        public final CaseFileService cases;
        public final CorrectionService corrections;
        public final DelegationService delegations;
        public final HolidayService holidays;
        public final DeliveryService deliveries;
        public final AppealService appeals;
        public final JudgeAssignmentService judges;
        public final AccessRequestService access;
        public final GeneralLetterService generalLetters;
        public final DictionaryService dictionaries;
        public final ArchiveService archive;
        public final ReportService reports;
        public final TemplateService templates;
        public final NoteService notes;
        public final CaseAccessService caseAccess;
        private final CaseAccessService caseAccess0;
        public final LetterService letters;
        public final SigningService signing;
        public final CaseDocumentService documents;
        public final SearchService search;

        public Services(EntityManager em, PreliminaryReviewProcess process) {
            this.em = em;
            this.process = process;
            this.audit = Wiring.inject(new AuditService() {
                @Override
                public AuditEvent recordDenial(String userId, String userRole, String action, String objectRef, String caseRef, String remoteAddr) {
                    // odpowiednik REQUIRES_NEW: osobna transakcja na osobnym EntityManagerze
                    return db.tx(other -> Wiring.inject(new AuditService(), other).record(userId, userRole, action, objectRef, caseRef, remoteAddr));
                }
            }, em);
            this.docketNumbers = Wiring.inject(new DocketNumberService(), em);
            this.delegations = Wiring.inject(new DelegationService(), em, audit, clock);
            this.holidays = Wiring.inject(new HolidayService(), em, audit);
            this.incoming = Wiring.inject(new IncomingRegister(), em, audit, clock);
            this.caseAccess0 = Wiring.inject(new CaseAccessService(), em, audit);
            this.search = Wiring.inject(new SearchService(), em, searchIndex, caseAccess0);
            this.cases = Wiring.inject(new CaseFileService(), em, docketNumbers, process, audit, clock, search);
            this.corrections = Wiring.inject(new CorrectionService(), em, audit, clock);
            this.deliveries = Wiring.inject(new DeliveryService(), em, holidays, process, audit);
            this.appeals = Wiring.inject(new AppealService(), em, cases, delegations, process, audit, clock);
            this.judges = Wiring.inject(new JudgeAssignmentService(), em, cases, delegations, engine.getTaskService(), audit, clock);
            this.caseAccess = caseAccess0;
            this.access = Wiring.inject(new AccessRequestService(), em, cases, audit, clock);
            this.generalLetters = Wiring.inject(new GeneralLetterService(), em, incoming, audit, clock);
            this.dictionaries = Wiring.inject(new DictionaryService(), em, audit);
            this.archive = Wiring.inject(new ArchiveService(), em, cases, store, audit, clock);
            this.reports = Wiring.inject(new ReportService(), em, clock);
            this.templates = Wiring.inject(new TemplateService(), em, audit, validator, clock);
            this.notes = Wiring.inject(new NoteService(), em, caseAccess, audit, clock);
            this.letters = Wiring.inject(new LetterService(), em, cases, templates, docs, store, engine.getTaskService(), audit, clock);
            this.signing = Wiring.inject(new SigningService(), em, cases, deliveries, docs, store, engine.getTaskService(), audit, clock);
            this.documents = Wiring.inject(new CaseDocumentService(), em, cases, store, audit, clock, search);
        }
    }

    @BeforeAll
    static void startDb() {
        db = new JpaTestSupport();
        clock = new AppClock(Clock.fixed(TODAY.atTime(9, 0).atZone(AppClock.WARSAW).toInstant(), AppClock.WARSAW));
        ClockUtil.setCurrentTime(Date.from(clock.instant()));
        store = new InMemoryDocumentStore();
        docs = new StubDocsGateway();
        searchIndex = new InMemorySearchIndex();
        validator = (code, xhtml) -> xhtml == null || xhtml.isBlank() || xhtml.contains("<script")
                ? new TemplateService.Validation(false, List.of("niedozwolona treść szablonu"))
                : new TemplateService.Validation(true, List.of());
    }

    @AfterAll
    static void stopDb() {
        ClockUtil.reset();
        if (db != null) {
            db.close();
        }
    }

    @AfterEach
    void cleanProcesses() {
        var runtime = engine.getRuntimeService();
        runtime.createProcessInstanceQuery().list().forEach(pi -> runtime.deleteProcessInstance(pi.getId(), "test cleanup"));
    }

    protected PreliminaryReviewProcess process() {
        return PreliminaryReviewProcess.forTests(engine.getRuntimeService(), engine.getManagementService(), engine.getTaskService());
    }

    protected <T> T tx(Function<Services, T> work) {
        return db.tx(em -> work.apply(new Services(em, process())));
    }

    /** Blok bez wyniku (lambdy z blokiem {@code { ... }}). */
    protected void run(Consumer<Services> work) {
        tx(s -> {
            work.accept(s);
            return null;
        });
    }

    protected List<AuditEvent> auditEvents() {
        return tx(s -> s.em.createQuery("select e from AuditEvent e order by e.id", AuditEvent.class).getResultList());
    }

    protected long auditCount(String actionPrefix) {
        return auditEvents().stream().filter(e -> e.getAction().startsWith(actionPrefix)).count();
    }

    /** Rejestruje pismo papierowe od Jana Przykładowego (UC-01). */
    protected IncomingLetter registerLetter(Services s) {
        return s.incoming.register(new IncomingRegister.RegisterCommand(IntakeChannel.POCZTA, Instant.parse("2026-10-30T08:15:00Z"),
                LocalDate.of(2026, 10, 28), false, "adw. Anna Testowa (pełnomocnik Jana Przykładowego)", 3, 5, false, "kprzykladowy", "10.0.0.7"));
    }

    /** Zakłada sprawę Ts ze skarżącym i pełnomocnikiem (UC-03); proces czeka na wyznaczenie sędziego. */
    protected CaseFile createCase(Services s, long letterId) {
        return s.cases.createCase(new CaseFileService.CreateCaseCommand(DocketSymbol.TS, letterId, List.of(
                new CaseFileService.PartyData(PartyRole.SKARZACY, "Jan Przykładowy", "ul. Przykładowa 1, 00-001 Warszawa", null, null, null, null, null),
                new CaseFileService.PartyData(PartyRole.PELNOMOCNIK, "adw. Anna Testowa", "ul. Testowa 2, 00-002 Warszawa", "AE:PL-00000-00000-AAAAA-00",
                        PowerOfAttorneyKind.SZCZEGOLNE, Profession.ADWOKAT, "WAW/Adw/0000", LocalDate.of(2026, 9, 1))),
                "ustawa z 1 stycznia 2020 r. o przykładach", "art. 5 ust. 2", "art. 45 ust. 1 Konstytucji",
                "Sąd Najwyższy", "I CSK 1/26", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), false, false, false, 14, REFERENT));
    }

    protected static Date date(LocalDate d) {
        return Date.from(d.atStartOfDay(AppClock.WARSAW).toInstant());
    }

    protected static Instant utc(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
