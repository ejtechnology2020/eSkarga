package pl.ejtechnology.tk.eSkarga.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import pl.ejtechnology.tk.eSkarga.audit.AuditEvent;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.CaseFlag;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineState;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.Tone;
import pl.ejtechnology.tk.eSkarga.domain.model.AccessRequest;
import pl.ejtechnology.tk.eSkarga.domain.model.Appeal;
import pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;
import pl.ejtechnology.tk.eSkarga.domain.model.Deadline;
import pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.Delegation;
import pl.ejtechnology.tk.eSkarga.domain.model.Delivery;
import pl.ejtechnology.tk.eSkarga.domain.model.GeneralLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.model.Publication;
import pl.ejtechnology.tk.eSkarga.domain.model.ReviewStandard;
import pl.ejtechnology.tk.eSkarga.domain.model.TemplateStatus;
import pl.ejtechnology.tk.eSkarga.domain.service.AccessRequestService;
import pl.ejtechnology.tk.eSkarga.domain.service.AppealService;
import pl.ejtechnology.tk.eSkarga.domain.service.ArchiveService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.domain.service.DeliveryService;
import pl.ejtechnology.tk.eSkarga.domain.service.DictionaryService;
import pl.ejtechnology.tk.eSkarga.domain.service.GeneralLetterService;
import pl.ejtechnology.tk.eSkarga.domain.service.HolidayService;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.domain.service.JudgeAssignmentService;
import pl.ejtechnology.tk.eSkarga.domain.service.ReportService;
import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;
import pl.ejtechnology.tk.eSkarga.process.FormalRequirementsService;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.process.TaskBoardService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.model.AccessRequestRow;
import pl.ejtechnology.tk.eSkarga.web.model.AppealRow;
import pl.ejtechnology.tk.eSkarga.web.model.ArchiveRow;
import pl.ejtechnology.tk.eSkarga.web.model.AuditRow;
import pl.ejtechnology.tk.eSkarga.web.model.CalendarMonth;
import pl.ejtechnology.tk.eSkarga.web.model.CaseSummary;
import pl.ejtechnology.tk.eSkarga.web.model.ChecklistItem;
import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;
import pl.ejtechnology.tk.eSkarga.web.model.DelegationRow;
import pl.ejtechnology.tk.eSkarga.web.model.DeliveryRow;
import pl.ejtechnology.tk.eSkarga.web.model.DictionaryRow;
import pl.ejtechnology.tk.eSkarga.web.model.GeneralLetterRow;
import pl.ejtechnology.tk.eSkarga.web.model.HBar;
import pl.ejtechnology.tk.eSkarga.web.model.IncomingLetterRow;
import pl.ejtechnology.tk.eSkarga.web.model.JudgeLoad;
import pl.ejtechnology.tk.eSkarga.web.model.KpiTile;
import pl.ejtechnology.tk.eSkarga.web.model.ProcessStep;
import pl.ejtechnology.tk.eSkarga.web.model.PublicationItem;
import pl.ejtechnology.tk.eSkarga.web.model.QueueItem;
import pl.ejtechnology.tk.eSkarga.web.model.RecordPage;
import pl.ejtechnology.tk.eSkarga.web.model.SearchHit;
import pl.ejtechnology.tk.eSkarga.web.model.TaskRow;
import pl.ejtechnology.tk.eSkarga.web.model.TemplateRow;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;
import pl.ejtechnology.tk.eSkarga.web.model.UserRow;

/**
 * Dane widoków z usług domenowych (JPA + Operaton) – implementacja produkcyjna {@link ViewData} (ADR 0017).
 * Każde zapytanie o sprawę przechodzi przez {@link CaseAccessPolicy} (D4). Teksty wskaźników pochodzą z {@link Fmt}.
 */
@Dependent
@Typed(JpaViewData.class)   // ViewData dostarcza tylko producent (ViewDataProducer)
@jakarta.transaction.Transactional   // jedna transakcja na wywołanie: encje z usług EJB pozostają zarządzane, więc leniwe relacje
                                     // (wersje wpisu, dokumenty) czytane przy budowie modeli widoków nie kończą się LazyInitializationException
public class JpaViewData implements ViewData {

    @Inject CaseFileService cases;
    @Inject IncomingRegister incoming;
    @Inject DeliveryService deliveries;
    @Inject AppealService appeals;
    @Inject GeneralLetterService generalLetters;
    @Inject AccessRequestService access;
    @Inject CaseDocumentService documents;
    @Inject JudgeAssignmentService judges;
    @Inject TaskBoardService board;
    @Inject ReportService reports;
    @Inject ArchiveService archive;
    @Inject DelegationService delegations;
    @Inject TemplateService templates;
    @Inject DictionaryService dictionaries;
    @Inject pl.ejtechnology.tk.eSkarga.domain.service.UserDirectoryService directory;
    @Inject pl.ejtechnology.tk.eSkarga.domain.service.NoteService notes;
    @Inject HolidayService holidays;
    @Inject CaseAccessService caseAccess;
    @Inject AuditService audit;
    @Inject FormalRequirementsService requirements;
    @Inject pl.ejtechnology.tk.eSkarga.domain.service.LetterService letters;
    @Inject pl.ejtechnology.tk.eSkarga.domain.service.ChecklistService checklist;
    @Inject CurrentUser user;
    @Inject pl.ejtechnology.tk.eSkarga.search.SearchService search;
    @Inject AppClock clock;
    @Inject Fmt fmt;

    // ------------------------------------------------------------------ sprawy

    @Override
    public CaseSummary caseBy(String sygnatura) {
        if (sygnatura == null || sygnatura.isBlank()) {
            return null;
        }
        try {
            return cases.byDocket(DocketNumber.parse(sygnatura)).filter(this::canRead).map(this::summary).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<CaseSummary> cases() {
        int year = clock.today().getYear();
        List<CaseSummary> out = new ArrayList<>();
        for (DocketSymbol s : DocketSymbol.values()) {
            cases.inDocket(s, year).stream().filter(this::canRead).map(this::summary).forEach(out::add);
        }
        return out;
    }

    @Override
    public List<CaseSummary> casesIn(DocketSymbol symbol, int year) {
        return cases.inDocket(symbol, year).stream().filter(this::canRead).map(this::summary).toList();
    }

    private boolean canRead(CaseFile cf) {
        return CaseAccessPolicy.canReadCase(user.getLogin(), user.getRoles(), caseAccess.assignment(cf));
    }

    CaseSummary summary(CaseFile cf) {
        CaseFileVersion v = cf.current();
        List<Party> parties = cases.parties(cf);
        String complainant = parties.stream().filter(p -> p.getRole() == PartyRole.SKARZACY || p.getRole() == PartyRole.WNIOSKODAWCA).map(Party::getDisplayName).findFirst().orElse("");
        String attorney = parties.stream().filter(p -> p.getRole() == PartyRole.PELNOMOCNIK).map(Party::getDisplayName).findFirst().orElse(null);
        Set<CaseFlag> flags = EnumSet.noneOf(CaseFlag.class);
        if (v.isFlagUrgent()) {
            flags.add(CaseFlag.PILNA_ART_79);
        }
        if (v.isFlagAnonymise()) {
            flags.add(CaseFlag.ANONIMIZACJA_ART_80);
        }
        if (v.isFlagSuspended()) {
            flags.add(CaseFlag.ZAWIESZONA);
        }
        if (v.getStatus().isReadOnly()) {
            flags.add(CaseFlag.TYLKO_DO_ODCZYTU);
        }
        DocketNumber related = null;
        try {
            related = v.getRelatedDocket() == null ? null : DocketNumber.parse(v.getRelatedDocket());
        } catch (IllegalArgumentException ignored) {
            // sygnatura SK/K/U poza wzorem Ts/Tw – pokazujemy jako tekst w przedmiocie
        }
        return new CaseSummary(cf.getDocket(), cf.getDocket().symbol() == DocketSymbol.TS ? "skarga konstytucyjna" : "wniosek", v.getStatus(), complainant, attorney,
                v.getJudgeUserId(), flags, runningDeadline(cf).orElse(DeadlineView.none(fmt.msg("deadline.none"))), related,
                v.getReviewSubject(), cf.getEnteredOn(), v.getClosingMode() == null ? null : closingLabel(v.getClosingMode()), v.getClosedOn(), "v" + v.getVersion());
    }

    static String closingLabel(ClosingMode m) {
        return switch (m) {
            case NADANIE_BIEGU -> "nadanie dalszego biegu";
            case ODMOWA_PRAWOMOCNA -> "odmowa prawomocna";
            case ZAZALENIE_NIEUWZGLEDNIONE -> "zażalenie nieuwzględnione";
            case BEZ_ROZPOZNANIA -> "bez rozpoznania";
            case UMORZENIE -> "umorzenie";
            case COFNIECIE -> "cofnięcie";
        };
    }

    Optional<DeadlineView> runningDeadline(CaseFile cf) {
        return deliveries.runningDeadlines(cf).stream().min(Comparator.comparing(Deadline::getEndsOn)).map(this::view);
    }

    DeadlineView view(Deadline d) {
        LocalDate today = clock.today();
        long left = ChronoUnit.DAYS.between(today, d.getEndsOn());
        DeadlineState state = d.getStatus() == DeadlineStatus.UCHYLONY ? DeadlineState.NONE : d.getStatus() != DeadlineStatus.BIEGNIE ? DeadlineState.AUXILIARY
                : left < 0 ? DeadlineState.OVERDUE : left <= 2 ? DeadlineState.WARNING : DeadlineState.NORMAL;
        long total = Math.max(1, ChronoUnit.DAYS.between(d.getStartsOn(), d.getEndsOn()));
        int progress = (int) Math.max(0, Math.min(100, 100 * ChronoUnit.DAYS.between(d.getStartsOn(), today) / total));
        return new DeadlineView(d.getKind(), state, d.getEndsOn(), progress, fmt.untilText(d.getEndsOn(), today), true);
    }

    // ------------------------------------------------------------------ pulpit

    @Override
    public Integer menuCounter(Screen s, AppRole role) {
        return switch (s) {
            case S01 -> nullIfZero(board.tasksFor(user.getLogin(), user.getRoles()).size());
            case S06 -> role == AppRole.TK_REFERENT ? nullIfZero(incoming.queue().size()) : null;
            case S10 -> role == AppRole.TK_REFERENT ? nullIfZero(deliveries.toSend().size() + deliveries.unconfirmed().size()) : null;
            case S17 -> role == AppRole.TK_PREZES ? nullIfZero(judges.awaiting().size()) : null;
            case S19 -> role == AppRole.TK_KIEROWNIK ? nullIfZero(board.dueWithin(2, user.getLogin(), user.getRoles()).size()) : null;
            default -> null;
        };
    }

    private static Integer nullIfZero(int n) {
        return n == 0 ? null : n;
    }

    @Override
    public List<KpiTile> kpisFor(AppRole role) {
        List<TaskBoardService.Item> tasks = board.tasksFor(user.getLogin(), user.getRoles());
        long soon = tasks.stream().filter(t -> t.daysLeft() != null && t.daysLeft() >= 0 && t.daysLeft() <= 2).count();
        long overdue = tasks.stream().filter(t -> t.daysLeft() != null && t.daysLeft() < 0).count();
        List<KpiTile> out = new ArrayList<>();
        out.add(new KpiTile(String.valueOf(tasks.size()), "Moje zadania", tasks.isEmpty() ? "brak zadań" : "zadania z procesu wstępnej kontroli", Tone.NEUTRAL, true, "#zad"));
        out.add(new KpiTile(String.valueOf(soon), "Terminy ≤ 2 dni", soon == 0 ? "brak terminów do " + fmt.date(clock.today().plusDays(2)) : "sprawdź listę zadań", soon > 0 ? Tone.WARN : Tone.NEUTRAL, false, "#zad"));
        out.add(new KpiTile(String.valueOf(overdue), "Po terminie", overdue == 0 ? "wszystko w terminie" : "wymaga czynności", overdue > 0 ? Tone.ERR : Tone.NEUTRAL, false, "#zad"));
        if (role == AppRole.TK_REFERENT || role == AppRole.TK_BIURO_PODAWCZE || role == AppRole.TK_KIEROWNIK) {
            int q = incoming.queue().size();
            out.add(new KpiTile(String.valueOf(q), "Pisma do kwalifikacji", q == 0 ? "kolejka pusta" : "najstarsze pierwsze", q > 5 ? Tone.WARN : Tone.NEUTRAL, false, Screen.S06.path()));
        } else {
            out.add(new KpiTile("0", "Nowe pisma w moich sprawach", "powiadomienia poniżej", Tone.NEUTRAL, false, "#zad"));
        }
        return out;
    }

    @Override
    public List<TaskRow> tasksFor(AppRole role) {
        List<TaskRow> out = new ArrayList<>();
        for (TaskBoardService.Item i : board.tasksFor(user.getLogin(), user.getRoles())) {
            CaseSummary cs = cases.byDocket(i.docket()).map(this::summary).orElse(null);
            if (cs == null) {
                continue;
            }
            DeadlineView dv = i.deadlineEnd() == null ? DeadlineView.none(fmt.waitingText(ChronoUnit.DAYS.between(i.createdAt().atZone(AppClock.WARSAW).toLocalDate(), clock.today())))
                    : new DeadlineView(DeadlineType.OCZEKIWANIE, i.daysLeft() < 0 ? DeadlineState.OVERDUE : i.daysLeft() <= 2 ? DeadlineState.WARNING : DeadlineState.NORMAL,
                            i.deadlineEnd(), null, fmt.untilText(i.deadlineEnd(), clock.today()), true);
            Tone tone = i.daysLeft() != null && i.daysLeft() < 0 ? Tone.ERR : i.nearDeadline() ? Tone.WARN : i.urgent() ? Tone.WARN : null;
            out.add(new TaskRow(i.taskId(), cs, i.name(), i.urgent() ? "sprawa pilna (art. 79)" : "", i.group() ? groupLabel(i.taskKey()) : "ja",
                    dv, i.createdAt().atZone(AppClock.WARSAW).toLocalDate(), actionLabel(i.taskKey()), actionUrl(i.taskKey(), i.docket()), true, i.group(), tone));
        }
        return out;
    }

    static String groupLabel(String taskKey) {
        return switch (taskKey) {
            case "T_Wyznacz", "T_Skieruj" -> "Prezes TK";
            case "T_RozpZaz", "T_BezRozp" -> "skład 3 sędziów";
            case "T_Wplyw" -> "biuro podawcze";
            default -> "referenci";
        };
    }

    static String actionLabel(String taskKey) {
        return switch (taskKey) {
            case "T_Wyznacz" -> "Wyznacz sędziego";
            case "T_Kontrola" -> "Lista kontrolna";
            case "T_Braki", "T_Odmowa", "T_Nadanie" -> "Projekt pisma";
            case "T_Skieruj" -> "Skieruj do składu";
            case "T_RozpZaz", "T_BezRozp" -> "Rozpoznaj zażalenie";
            case "T_Wpis" -> "Wpisz sprawę";
            case "T_Ogolne" -> "Odpowiedz";
            default -> "Otwórz akta";
        };
    }

    static String actionUrl(String taskKey, DocketNumber docket) {
        String syg = "?sygnatura=" + docket.format().replace(" ", "%20");
        return switch (taskKey) {
            case "T_Wyznacz" -> Screen.S17.path() + syg;
            case "T_Braki", "T_Odmowa", "T_Nadanie" -> Screen.S15.path() + syg;
            case "T_Skieruj", "T_RozpZaz", "T_BezRozp" -> Screen.S18.path() + syg;
            case "T_Wpis" -> Screen.S07.path();
            case "T_Ogolne" -> Screen.S12.path();
            default -> Screen.S14.path() + syg;
        };
    }

    @Override
    /** „Nowe w moich sprawach”: zdarzenia audytu przypięte do sprawy; odmowy dostępu i zdarzenia techniczne (logowanie, back-channel) zostają w dzienniku audytu. */
    public List<TimelineEntry> notificationsFor(AppRole role) {
        List<TimelineEntry> out = new ArrayList<>();
        for (AuditEvent e : audit.recent(60)) {
            if (e.getCaseRef() == null || e.getAction() == null || e.getAction().startsWith("odmowa")) {
                continue;
            }
            out.add(new TimelineEntry(at(e.getOccurredAt()), e.getUserId(), e.getAction() + " · " + e.getCaseRef(),
                    Screen.S14.path() + "?sygnatura=" + e.getCaseRef().replace(" ", "%20"), false, true));
            if (out.size() == 12) {
                break;
            }
        }
        return out;
    }

    static LocalDateTime at(Instant i) {
        return LocalDateTime.ofInstant(i, AppClock.WARSAW);
    }

    /**
     * Sędziowie do wyznaczenia (S-09): każdy, kto zalogował się do eSkargi z rolą {@code TK_SEDZIA} z ID-users (rejestr S-22, także bez spraw),
     * plus sędziowie mający sprawy we wstępnej kontroli (obciążenie z wersji wpisów). Bez rejestru lista była pusta do pierwszego przydziału.
     */
    @Override
    public List<JudgeLoad> judgeLoads() {
        java.util.Map<String, Long> loads = new java.util.TreeMap<>();
        for (JudgeAssignmentService.JudgeLoad l : judges.loads()) {
            loads.put(l.judgeUserId(), l.casesInReview());
        }
        java.util.Map<String, String> names = new java.util.HashMap<>();
        for (var k : directory.all()) {
            if (k.getRoles() != null && java.util.Arrays.asList(k.getRoles().split(",")).contains(AppRole.TK_SEDZIA.name())) {
                loads.putIfAbsent(k.getLogin(), 0L);
                names.put(k.getLogin(), k.getDisplayName());
            }
        }
        long max = loads.values().stream().mapToLong(Long::longValue).max().orElse(1);
        List<JudgeLoad> out = new ArrayList<>();
        for (var e : loads.entrySet()) {
            String name = names.getOrDefault(e.getKey(), e.getKey());
            out.add(new JudgeLoad(e.getKey(), name == null || name.isBlank() ? e.getKey() : name, e.getValue().intValue(), (int) (100 * e.getValue() / Math.max(1, max)), 0, "", null, null, false, true, null));
        }
        out.sort(java.util.Comparator.comparing(JudgeLoad::nazwisko, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ------------------------------------------------------------------ wyszukiwanie

    @Override
    public List<CaseSummary> searchCases(String query) {
        List<CaseSummary> out = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (pl.ejtechnology.tk.eSkarga.search.SearchIndex.Hit h : search.search(query, user.getLogin(), user.getRoles(), 50)) {
            if (seen.add(h.caseId())) {
                try {
                    out.add(summary(cases.require(h.caseId())));
                } catch (IllegalArgumentException gone) {
                    // sprawa usunięta z bazy, wpis w indeksie do odbudowy
                }
            }
        }
        if (out.isEmpty()) {
            cases.search(query, 50).stream().filter(this::canRead).map(this::summary).forEach(out::add);
        }
        return out;
    }

    @Override
    public List<SearchHit> searchHits(String query) {
        List<SearchHit> out = new ArrayList<>();
        for (pl.ejtechnology.tk.eSkarga.search.SearchIndex.Hit h : search.search(query, user.getLogin(), user.getRoles(), 50)) {
            if (h.documentId() == null) {
                continue;
            }
            DocketNumber dn = DocketNumber.parse(h.docket());
            out.add(new SearchHit(h.title(), Screen.S14.path() + "?sygnatura=" + h.docket().replace(" ", "%20") + "&dok=" + h.documentId(), dn, h.pages(), h.date(), "dokument", h.fragment(), ""));
        }
        return out;
    }

    // ------------------------------------------------------------------ wpływ

    @Override
    public List<TimelineEntry> recentIntake() {
        Instant now = clock.instant();
        List<TimelineEntry> out = new ArrayList<>();
        for (IncomingLetter l : incoming.between(now.minusSeconds(7 * 86_400L), now.plusSeconds(60))) {
            out.add(new TimelineEntry(at(l.getReceivedAt()), l.getCreatedBy(), l.getRegisterNo() + " · " + l.getSender(), null, false, true));
            if (out.size() >= 10) {
                break;
            }
        }
        return out;
    }

    @Override
    public List<IncomingLetterRow> incomingLetters() {
        Instant now = clock.instant();
        List<IncomingLetterRow> out = new ArrayList<>();
        for (IncomingLetter l : incoming.between(now.minusSeconds(60 * 86_400L), now.plusSeconds(60))) {
            boolean pending = l.getQualification() == null;
            long waiting = ChronoUnit.DAYS.between(l.getReceivedAt().atZone(AppClock.WARSAW).toLocalDate(), clock.today());
            out.add(new IncomingLetterRow(l.getRegisterNo(), at(l.getReceivedAt()), l.getFiledOn(), l.getChannel(), l.getSender(),
                    pending ? "do kwalifikacji" : l.getQualification().name().toLowerCase().replace('_', ' '), l.getCaseFile() == null ? null : l.getCaseFile().getDocket(),
                    l.isClassified() ? "dokument niejawny – tylko metadane" : "", pending ? "czeka " + fmt.days(waiting) : "zakwalifikowane",
                    pending && waiting > 2 ? Tone.WARN : Tone.NEUTRAL, pending && waiting > 2 ? Tone.WARN : null, false, 0, l.getAttachments()));
        }
        return out;
    }

    @Override
    public List<QueueItem> queue() {
        List<QueueItem> out = new ArrayList<>();
        for (IncomingLetter l : incoming.queue()) {
            long waiting = ChronoUnit.DAYS.between(l.getReceivedAt().atZone(AppClock.WARSAW).toLocalDate(), clock.today());
            List<CaseFile> related = cases.related(l.getSender(), null);
            String hint = related.isEmpty() ? "" : "ten sam nadawca: " + related.get(0).getDocket().format();
            out.add(new QueueItem(l.getRegisterNo(), l.getSender(), l.getChannel(), "czeka " + fmt.days(waiting), waiting > 2 ? Tone.WARN : Tone.NEUTRAL,
                    hint, !related.isEmpty(), false, waiting > 5 ? Tone.ERR : null));
        }
        return out;
    }

    // ------------------------------------------------------------------ rejestry

    @Override
    public List<DeliveryRow> toSend() {
        return deliveries.toSend().stream().map(d -> deliveryRow(d, "Wyślij", true)).toList();
    }

    @Override
    public List<DeliveryRow> unconfirmed() {
        return deliveries.unconfirmed().stream().map(d -> deliveryRow(d, "Zarejestruj zwrotkę", true)).toList();
    }

    @Override
    public List<DeliveryRow> delivered() {
        return deliveries.delivered().stream().map(d -> deliveryRow(d, "Koryguj", false)).toList();
    }

    private DeliveryRow deliveryRow(Delivery d, String action, boolean primary) {
        CaseFile cf = d.getDocument().getCaseFile();
        DeadlineView dv = deliveries.runningDeadlines(cf).stream().filter(x -> x.getDelivery() != null && x.getDelivery().getId().equals(d.getId())).findFirst().map(this::view)
                .orElse(DeadlineView.none(d.getDeliveredOn() == null ? "czeka na doręczenie" : "bez terminu"));
        String addresseeNote = d.getAddressee().getRole() == PartyRole.PELNOMOCNIK ? "pełnomocnik (BR-06)" : "";
        return new DeliveryRow(cf.getDocket(), d.getDocument().getTitle(), d.getAddressee().getDisplayName(), addresseeNote, d.getChannel().name().replace('_', ' '),
                d.getDeliveredOn() != null ? d.getDeliveredOn() : d.getSentOn(), d.getMethod() == null ? "" : d.getMethod().name().replace('_', ' '), dv, action, primary, false, null);
    }

    @Override
    public List<AppealRow> appeals() {
        List<AppealRow> out = new ArrayList<>();
        for (Appeal a : appeals.all()) {
            CaseFile cf = a.getCaseFile();
            CaseSummary cs = summary(cf);
            Optional<Deadline> dl = appeals.appealDeadline(cf);
            boolean inTime = Boolean.TRUE.equals(a.getInTime());
            DeadlineView tv = dl.map(this::view).orElse(DeadlineView.none("brak terminu"));
            out.add(new AppealRow(cf.getDocket(), cs.flagi(), dl.map(Deadline::getStartsOn).map(s -> s.minusDays(1)).orElse(null), a.getFiledOn(), "",
                    tv, a.getInTime() == null ? "do oceny" : inTime ? "w terminie" : "po terminie (pomocniczo)", a.getInTime() == null ? Tone.NEUTRAL : inTime ? Tone.OK : Tone.WARN,
                    a.getPanelUserIds() == null ? "—" : a.getPanelUserIds(), a.getSessionOn() == null ? "—" : fmt.date(a.getSessionOn()),
                    a.getSessionOn() == null ? null : new DeadlineView(DeadlineType.POSIEDZENIE, DeadlineState.NORMAL, a.getSessionOn(), null, fmt.untilText(a.getSessionOn(), clock.today()), true),
                    a.getOutcome() == null ? "w toku" : a.getOutcome().name().toLowerCase().replace('_', ' '), a.getOutcome() == null ? Tone.INFO : Tone.OFF,
                    a.getOutcome() == null ? (a.getPanelUserIds() == null ? "Skieruj do składu" : "Wprowadź wynik") : "Akta", Screen.S18.path() + "?sygnatura=" + cf.getDocket().format().replace(" ", "%20"), false, null));
        }
        return out;
    }

    @Override
    public List<GeneralLetterRow> generalLetters() {
        List<GeneralLetterRow> out = new ArrayList<>();
        for (GeneralLetter g : generalLetters.all()) {
            boolean open = g.getRepliedOn() == null;
            long left = ChronoUnit.DAYS.between(clock.today(), g.getReplyDueOn());
            DeadlineView dv = open ? new DeadlineView(DeadlineType.ODPOWIEDZ, left < 0 ? DeadlineState.OVERDUE : left <= 2 ? DeadlineState.WARNING : DeadlineState.NORMAL, g.getReplyDueOn(), null,
                    fmt.untilText(g.getReplyDueOn(), clock.today()), true) : DeadlineView.none("odpowiedź " + fmt.date(g.getRepliedOn()));
            out.add(new GeneralLetterRow(g.getLetter().getRegisterNo(), g.getLetter().getSender(), g.getSubject(), dv, open ? "do odpowiedzi" : "załatwione",
                    open ? (left < 0 ? Tone.ERR : Tone.INFO) : Tone.OFF, false, open && left < 0 ? Tone.ERR : null));
        }
        return out;
    }

    @Override
    public List<AccessRequestRow> accessRequests() {
        List<AccessRequestRow> out = new ArrayList<>();
        for (AccessRequest r : access.all()) {
            boolean third = !r.isParticipant();
            out.add(new AccessRequestRow(r.getRequester(), third ? "osoba trzecia" : "uczestnik", third ? Tone.WARN : Tone.NEUTRAL, r.getCaseFile().getDocket(), r.getScope(),
                    r.getReadingRoomAt() == null ? "nie wyznaczono" : fmt.dateTime(at(r.getReadingRoomAt())), r.getReadingRoomAt() != null,
                    r.getFeePln() == null ? "—" : r.getFeePln().toPlainString() + " zł", third ? (r.isAnonymisedReady() ? "gotowa" : "w przygotowaniu") : "nie dotyczy",
                    third ? (r.isAnonymisedReady() ? Tone.OK : Tone.WARN) : Tone.OFF, third, false));
        }
        return out;
    }

    @Override
    public List<PublicationItem> publications() {
        List<PublicationItem> out = new ArrayList<>();
        for (Publication p : access.pendingPublications()) {
            CaseFile cf = p.getDocument().getCaseFile();
            out.add(new PublicationItem(cf.getDocket(), p.getStatus().name().toLowerCase(), p.getStatus().name().equals("POPRAWKA") ? Tone.WARN : Tone.INFO,
                    cf.current().isFlagAnonymise(), p.getDocument().getTitle(), p.getAnonymisedKey() == null ? "brak wersji zanonimizowanej" : "", p.getReplacements(), false));
        }
        return out;
    }

    // ------------------------------------------------------------------ akta

    private Optional<CaseFile> readable(String sygnatura) {
        try {
            return cases.byDocket(DocketNumber.parse(sygnatura)).filter(this::canRead);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<RecordPage> recordPages(String sygnatura) {
        Optional<CaseFile> cf = readable(sygnatura);
        if (cf.isEmpty()) {
            return List.of();
        }
        boolean team = CaseAccessPolicy.canReadInternalNotes(user.getLogin(), user.getRoles(), caseAccess.assignment(cf.get()));
        List<RecordPage> out = new ArrayList<>();
        for (CaseDocument d : cases.documents(cf.get())) {
            if (d.isInternalNote() && !team) {
                continue; // FR-24
            }
            boolean startsDeadline = deliveries.deadlines(cf.get()).stream().anyMatch(x -> x.getDelivery() != null && x.getDelivery().getDocument().getId().equals(d.getId()));
            out.add(new RecordPage(String.valueOf(d.getId()), d.pagesLabel(), d.getDocumentDate(), d.getTitle(), d.getKind(),
                    d.isClassified() ? "dokument niejawny – tylko metadane" : (d.isPdfaCompliant() ? "PDF/A" : "oczekuje na przetworzenie") + (d.isOcrDone() ? ", OCR" : ""),
                    d.getCreatedAt() != null && d.getCreatedAt().isAfter(clock.instant().minusSeconds(86_400)), false, startsDeadline));
        }
        return out;
    }

    @Override
    public List<TimelineEntry> timeline(String sygnatura) {
        List<TimelineEntry> out = new ArrayList<>();
        if (readable(sygnatura).isEmpty()) {
            return out;
        }
        for (AuditEvent e : audit.forCase(DocketNumber.parse(sygnatura).format(), 30)) {
            out.add(new TimelineEntry(at(e.getOccurredAt()), e.getUserId(), e.getAction(), null, false, true));
        }
        return out;
    }

    @Override
    public List<ProcessStep> processSteps(String sygnatura) {
        Optional<CaseFile> cfo = readable(sygnatura);
        if (cfo.isEmpty()) {
            return List.of();
        }
        CaseFile cf = cfo.get();
        CaseStatus st = cf.current().getStatus();
        int stage = switch (st) {
            case ZAREJESTROWANA, OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO -> 1;
            case WE_WSTEPNEJ_KONTROLI -> 2;
            case WEZWANIE_DO_USUNIECIA_BRAKOW -> 3;
            case ODMOWA_TERMIN_NA_ZAZALENIE, ZAZALENIE_W_ROZPOZNANIU -> 4;
            case ZAWIESZONA -> 2;
            case ZAKONCZONA_NADANO_BIEG, ZAKONCZONA_ODMOWA_PRAWOMOCNA -> 5;
        };
        String[] labels = {"Wpis do repertorium", "Wyznaczenie sędziego", "Wstępna kontrola", "Braki / odmowa", "Zażalenie", "Zakończenie"};
        List<ProcessStep> out = new ArrayList<>();
        LocalDate[] dates = {cf.getEnteredOn(), null, null, null, null, cf.current().getClosedOn()};
        for (int i = 0; i < labels.length; i++) {
            boolean done = i < stage || (i == 5 && stage == 5);
            boolean current = i == stage && stage < 5;
            out.add(new ProcessStep(labels[i], dates[i], "", done, current, "", i + 1));
        }
        return out;
    }

    @Override
    public List<ChecklistItem> checklist(String sygnatura) {
        Optional<CaseFile> cfo = readable(sygnatura);
        if (cfo.isEmpty()) {
            return List.of();
        }
        CaseFile cf = cfo.get();
        List<Party> parties = cases.parties(cf);
        boolean attorney = parties.stream().anyMatch(p -> p.getRole() == PartyRole.PELNOMOCNIK);
        ProcessVariables.RodzajPisma rodzaj = cf.getDocket().symbol() == DocketSymbol.TS ? ProcessVariables.RodzajPisma.SKARGA : ProcessVariables.RodzajPisma.WNIOSEK;
        List<ChecklistItem> out = new ArrayList<>();
        Map<String, pl.ejtechnology.tk.eSkarga.domain.model.ChecklistEntry> oceny = checklist.current(cf);
        for (FormalRequirementsService.Requirement r : requirements.requirements(rodzaj, attorney, null)) {
            var e = oceny.get(r.code());
            ChecklistItem.State stan = e == null ? ChecklistItem.State.DO_SPRAWDZENIA : ChecklistItem.State.valueOf(e.getState().name());
            out.add(new ChecklistItem(r.description(), r.legalBasis(), stan, e == null || e.getNote() == null ? "" : e.getNote(), e != null && stan == ChecklistItem.State.BRAK,
                    e == null || e.getPageRef() == null ? "" : e.getPageRef(), r.code()));
        }
        return out;
    }

    /** Projekty (PROJEKT) z plikiem w aktach – rodzaj pisma odtworzony z podtypu czynności (ODMOWA_PKT_n → POST_ODMOWA). */
    @Override
    public List<pl.ejtechnology.tk.eSkarga.web.model.DraftRow> drafts(String sygnatura) {
        Optional<CaseFile> cfo = readable(sygnatura);
        if (cfo.isEmpty()) {
            return List.of();
        }
        List<pl.ejtechnology.tk.eSkarga.web.model.DraftRow> out = new ArrayList<>();
        for (var act : letters.acts(cfo.get())) {
            if (act.getStatus() != pl.ejtechnology.tk.eSkarga.domain.model.ActStatus.PROJEKT || act.getDocument() == null) {
                continue;
            }
            String sub = act.getSubtype() == null ? "" : act.getSubtype();
            String kind = sub.startsWith("ODMOWA") ? pl.ejtechnology.tk.eSkarga.domain.service.LetterKind.POST_ODMOWA.name()
                    : java.util.Arrays.stream(pl.ejtechnology.tk.eSkarga.domain.service.LetterKind.values()).filter(k -> sub.equals(k.subtype())).map(Enum::name).findFirst().orElse(null);
            if (kind == null) {
                continue;
            }
            out.add(new pl.ejtechnology.tk.eSkarga.web.model.DraftRow(act.getDocument().getId(), kind, act.getDocument().getTitle(), act.getAuthorUserId(), at(act.getActedOn())));
        }
        return out;
    }

    /** Notatki wewnętrzne (FR-24): usługa sama odmawia poza zespołem sędziego (pusta lista), autor po nazwie z rejestru S-22. */
    @Override
    public List<pl.ejtechnology.tk.eSkarga.web.model.NoteRow> notes(String sygnatura) {
        Optional<CaseFile> cfo = readable(sygnatura);
        if (cfo.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = new java.util.HashMap<>();
        for (var k : directory.all()) {
            if (k.getDisplayName() != null && !k.getDisplayName().isBlank()) {
                names.put(k.getLogin(), k.getDisplayName());
            }
        }
        List<pl.ejtechnology.tk.eSkarga.web.model.NoteRow> out = new ArrayList<>();
        for (var n : notes.notes(cfo.get().getId(), user.getLogin(), user.getRoles())) {
            out.add(new pl.ejtechnology.tk.eSkarga.web.model.NoteRow(n.getId(), n.getTitle(), n.getBody(), names.getOrDefault(n.getAuthorUserId(), n.getAuthorUserId()), at(n.getCreatedAt())));
        }
        return out;
    }

    @Override
    public boolean canReadNotes(String sygnatura) {
        return readable(sygnatura).map(cf -> notes.canRead(cf, user.getLogin(), user.getRoles())).orElse(false);
    }

    @Override
    public List<DeadlineView> deadlines(String sygnatura) {
        return readable(sygnatura).map(cf -> deliveries.deadlines(cf).stream().map(this::view).toList()).orElse(List.of());
    }

    // ------------------------------------------------------------------ nadzór i raporty

    @Override
    public List<HBar> clerkLoads() {
        Map<String, Integer> counts = new TreeMap<>();
        for (CaseAssignment a : caseAccess.activeAssignments()) {
            if (a.getRole() == AssignmentRole.CLERK) {
                counts.merge(a.getUserId(), 1, Integer::sum);
            }
        }
        return bars(counts);
    }

    @Override
    public List<HBar> judgeBars() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        judges.loads().forEach(l -> counts.put(l.judgeUserId(), (int) l.casesInReview()));
        return bars(counts);
    }

    @Override
    public List<HBar> outcomes() {
        int year = clock.today().getYear();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DocketSymbol s : DocketSymbol.values()) {
            ReportService.Summary sum = reports.summary(s.code(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            sum.byMode().forEach((m, n) -> counts.merge(closingLabel(m), n.intValue(), Integer::sum));
        }
        return bars(counts);
    }

    private static List<HBar> bars(Map<String, Integer> counts) {
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        List<HBar> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(new HBar(k, v, 100 * v / Math.max(1, max), false)));
        return out;
    }

    @Override
    public int[] monthlyIntake() {
        return monthly(true);
    }

    @Override
    public int[] monthlyDisposed() {
        return monthly(false);
    }

    private int[] monthly(boolean intake) {
        int year = clock.today().getYear();
        int[] out = new int[12];
        for (DocketSymbol s : DocketSymbol.values()) {
            ReportService.Summary sum = reports.summary(s.code(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            (intake ? sum.intakeByMonth() : sum.disposedByMonth()).forEach((m, n) -> out[m - 1] += n.intValue());
        }
        return out;
    }

    @Override
    public List<ArchiveRow> archive(boolean pdfaError) {
        List<ArchiveRow> out = new ArrayList<>();
        for (CaseFile cf : archive.closedNotArchived()) {
            CaseFileVersion v = cf.current();
            ArchiveService.Readiness r = archive.readiness(cf);
            var cat = archive.categoryFor(v.getClosingMode());
            String retention = cat.map(c -> c.getRetentionYears() == null ? "wieczyście (A)" : "do " + (v.getClosedOn().getYear() + c.getRetentionYears())).orElse("brak kategorii");
            out.add(new ArchiveRow(cf.getDocket(), closingLabel(v.getClosingMode()) + " " + fmt.date(v.getClosedOn()), cat.map(c -> c.getCategory()).orElse("?"), retention,
                    r.ready() ? "komplet PDF/A" : "brak PDF/A: " + r.missingPdfa().size(), r.ready() ? Tone.OK : Tone.ERR, v.getClosingMode() != ClosingMode.NADANIE_BIEGU, false, r.pages()));
        }
        return out;
    }

    @Override
    /** S-22: osoby znane eSkardze (rejestr z logowań: nazwa, role z tokenu, ostatnie logowanie) połączone z przydziałami i upoważnieniami. */
    public List<UserRow> users() {
        Map<String, List<CaseAssignment>> byUser = new TreeMap<>();
        for (CaseAssignment a : caseAccess.activeAssignments()) {
            byUser.computeIfAbsent(a.getUserId(), k -> new ArrayList<>()).add(a);
        }
        List<UserRow> out = new ArrayList<>();
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (var k : directory.all()) {
            List<CaseAssignment> list = byUser.getOrDefault(k.getLogin(), List.of());
            String roles = java.util.Arrays.stream(k.getRoles().split(",")).filter(r -> !r.isBlank()).map(r -> fmt.msg("role." + r)).reduce((a, b) -> a + ", " + b).orElse("—");
            boolean delegated = !delegations.policyDelegations(k.getLogin()).isEmpty();
            out.add(new UserRow(k.getDisplayName() == null || k.getDisplayName().isBlank() ? k.getLogin() : k.getDisplayName() + " (" + k.getLogin() + ")", "ID-users", roles,
                    at(k.getLastLoginAt()), list.isEmpty() ? "—" : list.size() + " spraw", !list.isEmpty(), delegated ? "tak" : "", false));
            listed.add(k.getLogin());
        }
        byUser.forEach((u, list) -> {
            if (listed.contains(u)) {
                return;
            }
            String roles = list.stream().map(a -> a.getRole().name()).distinct().sorted().reduce((a, b) -> a + ", " + b).orElse("");
            boolean delegated = !delegations.policyDelegations(u).isEmpty();
            out.add(new UserRow(u, "ID-users", roles, null, list.size() + " spraw", true, delegated ? "tak" : "", false));
        });
        return out;
    }

    @Override
    public List<DelegationRow> delegations() {
        List<DelegationRow> out = new ArrayList<>();
        LocalDate today = clock.today();
        for (Delegation d : delegations.all()) {
            boolean revoked = d.getRevokedAt() != null;
            boolean active = !revoked && !today.isBefore(d.getValidFrom()) && !today.isAfter(d.getValidTo());
            boolean expiring = active && !today.plusDays(14).isBefore(d.getValidTo());
            String status = revoked ? "odwołane" : active ? (expiring ? "wygasa " + fmt.date(d.getValidTo()) : "ważne") : today.isBefore(d.getValidFrom()) ? "przyszłe" : "wygasło";
            Tone tone = revoked ? Tone.OFF : active ? (expiring ? Tone.WARN : Tone.OK) : Tone.OFF;
            out.add(new DelegationRow(String.valueOf(d.getId()), d.getUserId(), d.getFunctionCode(), d.getValidFrom(), d.getValidTo(), d.getBasisDocRef(), d.getCreatedBy(),
                    status, tone, revoked ? "x" : active ? "check" : "clock", false, expiring ? Tone.WARN : null));
        }
        return out;
    }

    @Override
    public List<TemplateRow> templates() {
        List<TemplateRow> out = new ArrayList<>();
        for (LetterTemplate t : templates.all()) {
            Tone tone = t.getStatus() == TemplateStatus.AKTYWNA ? Tone.OK : t.getStatus() == TemplateStatus.PROJEKT ? Tone.INFO : Tone.OFF;
            out.add(new TemplateRow(t.getName(), t.getCode(), "v" + t.getVersion(), t.getCreatedAt() == null ? "" : fmt.dateTime(at(t.getCreatedAt())) + " · " + t.getAuthorUserId(),
                    t.getStatus().name().toLowerCase(), tone, false));
        }
        return out;
    }

    @Override
    public List<pl.ejtechnology.tk.eSkarga.web.model.ScanRow> scansOf(String registerNo) {
        List<pl.ejtechnology.tk.eSkarga.web.model.ScanRow> out = new ArrayList<>();
        if (registerNo == null || registerNo.isBlank()) {
            return out;
        }
        incoming.byRegisterNo(registerNo).ifPresent(l -> {
            for (var s : incoming.scans(l.getId())) {
                out.add(new pl.ejtechnology.tk.eSkarga.web.model.ScanRow(s.getId(), s.getFileName(), s.getContentType(), s.getSizeBytes()));
            }
        });
        return out;
    }

    @Override
    public java.util.Map<String, Integer> dictionaryCounts() {
        return java.util.Map.of("sady", dictionaries.courts().size(), "organy", dictionaries.authorities().size(),
                "typyPism", dictionaries.letterTypes().size(), "wzorce", dictionaries.reviewStandards().size());
    }

    @Override
    public List<DictionaryRow> reviewStandards() {
        List<DictionaryRow> out = new ArrayList<>();
        for (ReviewStandard s : dictionaries.reviewStandards()) {
            out.add(new DictionaryRow(s.getCode(), s.getProvision(), s.getDescription(), 0, s.isActive(), false));
        }
        return out;
    }

    @Override
    public List<AuditRow> audit() {
        List<AuditRow> out = new ArrayList<>();
        for (AuditEvent e : audit.recent(200)) {
            out.add(new AuditRow(e.getId(), at(e.getOccurredAt()), e.getUserId(), e.getUserRole() == null ? "" : e.getUserRole(), e.getAction(),
                    e.getCaseRef() == null ? "" : e.getCaseRef(), e.getObjectRef() == null ? "" : e.getObjectRef(), e.getRemoteAddr() == null ? "" : e.getRemoteAddr(),
                    e.getHash(), e.getPrevHash(), "", e.getUserId(), false));
        }
        return out;
    }

    @Override
    public List<CalendarMonth> calendar(int year) {
        Map<LocalDate, String> own = new LinkedHashMap<>();
        holidays.all().forEach(h -> own.put(h.getDay(), h.getDescription()));
        return CalendarBuilder.build(year, own);
    }

    @Override
    public Set<LocalDate> extraDaysOff(int year) {
        return CalendarBuilder.ofYear(holidays.extraDaysOff(), year);
    }
}
