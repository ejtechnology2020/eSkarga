package pl.ejtechnology.tk.eSkarga.web.demo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.CaseFlag;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineState;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.Tone;
import pl.ejtechnology.tk.eSkarga.domain.deadline.PolishHolidays;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.Screen;
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
import pl.ejtechnology.tk.eSkarga.web.model.Person;
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
 * Dane przykładowe z briefu makiet (§9) – wyłącznie fikcyjne osoby i sygnatury. „Dziś” to wtorek 03.11.2026.
 * Zestaw zasila beany widoków do czasu podłączenia usług domenowych (T-010 – T-016); wtedy beany dostają
 * te same rekordy z repozytoriów JPA, a ta klasa zostaje jako fixture testów UI (Playwright).
 */
@Named("demo")
@ApplicationScoped
@Typed(DemoDataset.class)   // ViewData dostarcza tylko producent (ViewDataProducer) – bez dwuznaczności w CDI
public class DemoDataset implements Serializable, pl.ejtechnology.tk.eSkarga.web.ViewData {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Inject
    private Fmt fmt;

    private final Map<String, CaseSummary> cases = new LinkedHashMap<>();
    private final Map<AppRole, Person> persons = new EnumMap<>(AppRole.class);

    @PostConstruct
    void init() {
        persons.put(AppRole.TK_BIURO_PODAWCZE, new Person("kwzorek", "Karolina Wzorek", "biuro podawcze", "KW", AppRole.TK_BIURO_PODAWCZE, false, 1));
        persons.put(AppRole.TK_REFERENT, new Person("mprzykladowski", "Marek Przykładowski", "referent Wydziału Weryfikacyjnego", "MP", AppRole.TK_REFERENT, false, 3));
        persons.put(AppRole.TK_KIEROWNIK, new Person("eszablon", "Ewa Szablon", "kierownik wydziału", "ES", AppRole.TK_KIEROWNIK, false, 4));
        persons.put(AppRole.TK_ASYSTENT, new Person("tprobny", "Tomasz Próbny", "asystent sędziego Piotra Fikcyjnego", "TP", AppRole.TK_ASYSTENT, false, 1));
        persons.put(AppRole.TK_SEDZIA, new Person("pfikcyjny", "sędzia TK Piotr Fikcyjny", "sędzia", "PF", AppRole.TK_SEDZIA, false, 2));
        persons.put(AppRole.TK_PREZES, new Person("mmakietowa", "sędzia TK Maria Makietowa", "wykonuje obowiązki Prezesa TK", "MM", AppRole.TK_PREZES, true, 2));
        persons.put(AppRole.TK_ARCHIWISTA, new Person("jarchiwalny", "Jan Archiwalny", "archiwista", "JA", AppRole.TK_ARCHIWISTA, false, 0));
        persons.put(AppRole.TK_ADMIN, new Person("aadmin", "Anna Admin", "administrator", "AA", AppRole.TK_ADMIN, false, 0));

        put(new CaseSummary(sig("Ts 312/26"), "Skarga konstytucyjna · wstępna kontrola", CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW,
                "Jan Przykładowy", "adw. Anna Testowa", "sędzia TK Piotr Fikcyjny", Set.of(),
                braki312(), null, "art. 505¹ § 1 k.p.c.", d(2026, 10, 14), null, null, "v1"));
        put(new CaseSummary(sig("Ts 315/26"), "Skarga konstytucyjna · wstępna kontrola", CaseStatus.WE_WSTEPNEJ_KONTROLI,
                "Zofia Zmyślona", "r.pr. Krzysztof Wzorowy", "sędzia TK Ewa Wzorcowa", Set.of(),
                dl(DeadlineType.ZWROTKA, DeadlineState.OVERDUE, null, 100, "18 dni · 4 dni po terminie", true), null,
                "art. 7 ust. 1 ustawy o świadczeniach rodzinnych", d(2026, 10, 23), null, null, "v2 · skorygowany"));
        put(new CaseSummary(sig("Ts 318/26"), "Skarga konstytucyjna · wstępna kontrola", CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO,
                "Przykładowa Spółka z o.o.", "r.pr. Oleńka Testowa", null, Set.of(),
                DeadlineView.none(fmt.waitingText(6) + " · brak biegnącego terminu"), null,
                "art. 15 ust. 2 ustawy o podatku od towarów i usług", d(2026, 10, 28), null, null, "v1"));
        put(new CaseSummary(sig("Ts 301/26"), "Skarga konstytucyjna · wstępna kontrola", CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE,
                "Robert Fikcyjny", "adw. Jan Makieta", "sędzia TK Adam Testowy", Set.of(CaseFlag.PILNA_ART_79),
                dl(DeadlineType.ZAZALENIE, DeadlineState.WARNING, d(2026, 11, 4), 86, fmt.untilText(d(2026, 11, 4), TODAY), true), null,
                "art. 258 § 1 k.p.k.", d(2026, 9, 22), null, null, "v1"));
        put(new CaseSummary(sig("Ts 287/26"), "Skarga konstytucyjna", CaseStatus.ZAKONCZONA_NADANO_BIEG,
                "Halina Testowa", "adw. Anna Testowa", "sędzia TK Ewa Wzorcowa", Set.of(CaseFlag.TYLKO_DO_ODCZYTU),
                DeadlineView.none("brak biegnącego terminu"), sig("SK 41/26"),
                "art. 81 ustawy o emeryturach i rentach z FUS", d(2026, 8, 12), "nadanie dalszego biegu", d(2026, 10, 24), "v1"));
        put(new CaseSummary(sig("Ts 276/26"), "Skarga konstytucyjna · zażalenie", CaseStatus.ZAZALENIE_W_ROZPOZNANIU,
                "Stefan Przykładny", "— (radca prawny we własnej sprawie)", "sędzia TK Piotr Fikcyjny", Set.of(CaseFlag.ANONIMIZACJA_ART_80),
                dl(DeadlineType.POSIEDZENIE, DeadlineState.NORMAL, d(2026, 11, 12), 40, "12.11.2026 · " + fmt.daysText(9), true), null,
                "art. 394¹ § 1 k.p.c.", d(2026, 7, 30), null, null, "v1"));
        put(new CaseSummary(sig("Tw 14/26"), "Wniosek · art. 191 ust. 1 pkt 3", CaseStatus.WE_WSTEPNEJ_KONTROLI,
                "Rada Gminy Przykładowo", "—", "sędzia TK Adam Testowy", Set.of(),
                DeadlineView.none("brak biegnącego terminu"), null, "art. 4 ust. 2 ustawy o dochodach JST", d(2026, 10, 2), null, null, "v1"));
        put(new CaseSummary(sig("Ts 240/26"), "Skarga konstytucyjna", CaseStatus.ZAKONCZONA_ODMOWA_PRAWOMOCNA,
                "Lech Zmyślony", "adw. Jan Makieta", "sędzia TK Maria Makietowa", Set.of(CaseFlag.TYLKO_DO_ODCZYTU),
                DeadlineView.none("do archiwum"), null, "art. 46 § 1 k.k.w.", d(2026, 6, 18), "odmowa nadania dalszego biegu", d(2026, 9, 15), "v1"));
        put(new CaseSummary(sig("Ts 319/26"), "Skarga konstytucyjna · wstępna kontrola", CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO,
                "Stefan Przykładny", "—", null, Set.of(), DeadlineView.none(fmt.waitingText(0)), null,
                "art. 394¹ § 1 k.p.c.", d(2026, 11, 3), null, null, "v1"));
    }

    // ------------------------------------------------------------------ pomocnicze

    public static DocketNumber sig(String s) {
        return DocketNumber.parse(s);
    }

    public static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    public static LocalDateTime dt(int y, int m, int day, int h, int min) {
        return LocalDateTime.of(y, m, day, h, min);
    }

    private static DeadlineView dl(DeadlineType t, DeadlineState s, LocalDate end, Integer progress, String text, boolean prefix) {
        return new DeadlineView(t, s, end, progress, text, prefix);
    }

    private DeadlineView braki312() {
        return dl(DeadlineType.BRAKI, DeadlineState.NORMAL, d(2026, 11, 9), 14, fmt.untilText(d(2026, 11, 9), TODAY), true);
    }

    private void put(CaseSummary c) {
        cases.put(c.sygnatura().format(), c);
    }

    public CaseSummary caseBy(String sygnatura) {
        CaseSummary c = cases.get(sygnatura);
        if (c == null) {
            try {
                c = cases.get(DocketNumber.parse(sygnatura).format());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return c;
    }

    public List<CaseSummary> cases() {
        return new ArrayList<>(cases.values());
    }

    @Override
    public List<CaseSummary> casesIn(pl.ejtechnology.tk.eSkarga.domain.DocketSymbol symbol, int year) {
        return cases.values().stream().filter(c -> c.sygnatura().symbol() == symbol && c.sygnatura().year() == year).toList();
    }

    public Person personFor(AppRole role) {
        return persons.getOrDefault(role, persons.get(AppRole.TK_REFERENT));
    }

    public List<Person> persons() {
        return new ArrayList<>(persons.values());
    }

    /** Liczniki przy pozycjach menu (brief §4.1), zależne od roli. */
    public Integer menuCounter(Screen s, AppRole role) {
        return switch (s) {
            case S01 -> switch (role) {
                case TK_REFERENT -> 5; case TK_ASYSTENT -> 4; case TK_SEDZIA -> 2; case TK_PREZES -> 3;
                case TK_BIURO_PODAWCZE -> 6; case TK_KIEROWNIK -> 7; default -> null;
            };
            case S06 -> role == AppRole.TK_REFERENT ? 4 : null;
            case S09 -> role == AppRole.TK_KIEROWNIK ? 1 : null;
            case S15 -> role == AppRole.TK_ASYSTENT ? 2 : role == AppRole.TK_SEDZIA ? 1 : null;
            case S16 -> role == AppRole.TK_SEDZIA || role == AppRole.TK_PREZES ? 2 : null;
            case S17, S18 -> role == AppRole.TK_PREZES ? 1 : null;
            case S10 -> role == AppRole.TK_REFERENT ? 3 : null;
            case S19 -> role == AppRole.TK_KIEROWNIK ? 9 : null;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ S-01 pulpit

    public List<KpiTile> kpisFor(AppRole role) {
        return switch (role) {
            case TK_ASYSTENT, TK_SEDZIA -> List.of(
                    new KpiTile("4", "Moje zadania", "kontrola, projekty, notatki", Tone.NEUTRAL, true, "#zad"),
                    new KpiTile("0", "Terminy ≤ 2 dni", "brak terminów do 05.11.2026", Tone.NEUTRAL, false, "#zad"),
                    new KpiTile("0", "Po terminie", "wszystko w terminie", Tone.NEUTRAL, false, "#zad"),
                    new KpiTile("1", "Nowe pisma w moich sprawach", "Ts 312/26 · dziś 08:42", Tone.NEUTRAL, false, "#zad"));
            case TK_PREZES -> List.of(
                    new KpiTile("3", "Moje zadania", "Prezes TK: 2 · sędzia: 1", Tone.NEUTRAL, true, "#zad"),
                    new KpiTile("1", "Terminy ≤ 2 dni", "Ts 301/26 · zażalenie do 04.11.2026 (jutro)", Tone.WARN, false, "#zad"),
                    new KpiTile("0", "Po terminie", "wszystko w terminie", Tone.NEUTRAL, false, "#zad"),
                    new KpiTile("1", "Sprawy bez sędziego", "Ts 318/26 · 6 dni oczekiwania", Tone.NEUTRAL, false, "#zad"));
            case TK_BIURO_PODAWCZE -> List.of(
                    new KpiTile("6", "Pisma do zarejestrowania", "w tym 2 z e-Doręczeń", Tone.NEUTRAL, true, "#zad"),
                    new KpiTile("1", "Niezakwalifikowane ponad 1 dzień", "DW 2026/04805 · 4 dni", Tone.ERR, false, "#zad"),
                    new KpiTile("7", "Zarejestrowane dziś", "od 07:30", Tone.NEUTRAL, false, "#zad"),
                    new KpiTile("2", "Dokumenty z klauzulą", "tylko metadane", Tone.NEUTRAL, false, "#zad"));
            case TK_KIEROWNIK -> List.of(
                    new KpiTile("7", "Zadania zespołu", "3 referenci", Tone.NEUTRAL, true, "#zad"),
                    new KpiTile("1", "Terminy ≤ 2 dni", "Ts 301/26 · zażalenie do 04.11.2026 (jutro)", Tone.WARN, false, "#zad"),
                    new KpiTile("2", "Po terminie", "DW 2026/04805 · Ts 315/26", Tone.ERR, false, "#zad"),
                    new KpiTile("1", "Korekty do akceptacji", "Ts 318/26", Tone.NEUTRAL, false, "#zad"));
            default -> List.of(
                    new KpiTile("5", "Moje zadania", "w tym 1 zadanie grupy do przejęcia", Tone.NEUTRAL, true, "#zad"),
                    new KpiTile("1", "Terminy ≤ 2 dni", "Ts 301/26 · zażalenie do 04.11.2026 (jutro)", Tone.WARN, false, "#zad"),
                    new KpiTile("1", "Po terminie", "brak zwrotki od 18 dni · Ts 315/26", Tone.ERR, false, "#zad"),
                    new KpiTile("2", "Nowe pisma w moich sprawach", "od wczoraj, 15:00", Tone.NEUTRAL, false, "#zad"));
        };
    }

    public List<TaskRow> tasksFor(AppRole role) {
        CaseSummary ts312 = caseBy("Ts 312/26");
        CaseSummary ts315 = caseBy("Ts 315/26");
        CaseSummary ts301 = caseBy("Ts 301/26");
        CaseSummary ts318 = caseBy("Ts 318/26");
        CaseSummary ts276 = caseBy("Ts 276/26");
        return switch (role) {
            case TK_ASYSTENT, TK_SEDZIA -> List.of(
                    new TaskRow("a1", ts312, "Sprawdź pismo uzupełniające", "DW 2026/04812 · czy usuwa brak pełnomocnictwa szczególnego", null,
                            braki312(), d(2026, 11, 3), "Otwórz akta", "/akta/podglad.xhtml?sygnatura=Ts%20312/26", true, false, Tone.NEUTRAL),
                    new TaskRow("a2", ts312, "Przygotuj projekt postanowienia", "Po ocenie pisma: nadanie dalszego biegu albo odmowa", null,
                            dl(DeadlineType.BRAKI, DeadlineState.NORMAL, null, null, "po 09.11.2026", false), d(2026, 11, 3),
                            "Nowy projekt", "/czynnosci/edytor.xhtml", false, false, Tone.NEUTRAL),
                    new TaskRow("a3", ts276, "Projekt zwrócony z uwagami", "Postanowienie w sprawie zażalenia · v2 z 3 · 2 uwagi sędziego", null,
                            dl(DeadlineType.POSIEDZENIE, DeadlineState.NORMAL, d(2026, 11, 12), 40, "12.11.2026 · " + fmt.daysText(9), true),
                            d(2026, 11, 2), "Popraw projekt", "/czynnosci/edytor.xhtml?stan=sklad", false, false, Tone.NEUTRAL),
                    new TaskRow("a4", ts276, "Przygotuj notatkę na posiedzenie", "Skład: Wzorcowa, Testowy, Makietowa", null,
                            dl(DeadlineType.POSIEDZENIE, DeadlineState.NORMAL, d(2026, 11, 10), null, fmt.untilText(d(2026, 11, 10), TODAY), false),
                            d(2026, 10, 30), "Notatka", "#", false, false, Tone.NEUTRAL));
            case TK_PREZES -> List.of(
                    new TaskRow("p1", ts301, "Skieruj zażalenie do składu", "Termin na wniesienie upływa jutro; zażalenie jeszcze nie wpłynęło", "Prezes TK",
                            ts301.termin(), d(2026, 10, 28), "Przygotuj skład", "/czynnosci/sklad.xhtml", false, false, Tone.WARN),
                    new TaskRow("p2", ts318, "Wyznacz sędziego do wstępnej kontroli", "Przykładowa Spółka z o.o. · r.pr. Oleńka Testowa", "Prezes TK",
                            DeadlineView.none(fmt.waitingText(6)), d(2026, 10, 28), "Wyznacz", "/czynnosci/wyznaczenie.xhtml", true, false, Tone.NEUTRAL),
                    new TaskRow("p3", ts276, "Posiedzenie niejawne w sprawie zażalenia", "Skład: Wzorcowa, Testowy, Makietowa", "sędzia",
                            ts276.termin(), d(2026, 10, 13), "Akta", "/akta/podglad.xhtml?sygnatura=Ts%20276/26", false, false, Tone.NEUTRAL));
            case TK_BIURO_PODAWCZE -> List.of(
                    new TaskRow("b1", null, "Zarejestruj pismo z e-Doręczeń", "Rada Gminy Przykładowo · UPO 02.11.2026 23:14", null,
                            DeadlineView.none("czeka " + fmt.days(1)), d(2026, 11, 2), "Zarejestruj", "/wplyw/rejestracja.xhtml?stan=el", true, false, Tone.NEUTRAL),
                    new TaskRow("b2", null, "Wydrukuj etykietę DW 2026/04812", "adw. Anna Testowa · poczta", null,
                            DeadlineView.none("dziś 08:42"), d(2026, 11, 3), "Etykieta", "/wplyw/etykieta.xhtml", false, false, Tone.NEUTRAL));
            case TK_KIEROWNIK -> List.of(
                    new TaskRow("k1", ts318, "Zatwierdź korektę wpisu", "Pełnomocnik, przedmiot · wniosek Marka Przykładowskiego", null,
                            DeadlineView.none("czeka od 08:55"), d(2026, 11, 3), "Korekta", "/sprawy/korekta.xhtml", true, false, Tone.NEUTRAL),
                    new TaskRow("k2", ts315, "Brak zwrotki ponad 14 dni", "Zawiadomienie o wyznaczeniu sędziego · Marek Przykładowski", null,
                            ts315.termin(), d(2026, 10, 16), "Monitor", "/nadzor/monitor.xhtml", false, false, Tone.ERR),
                    new TaskRow("k3", null, "Pismo niezakwalifikowane ponad 1 dzień", "DW 2026/04805 · r.pr. Oleńka Testowa", null,
                            dl(DeadlineType.OCZEKIWANIE, DeadlineState.OVERDUE, null, 100, "4 dni · 3 dni po terminie", false), d(2026, 10, 30),
                            "Przekaż", "/nadzor/monitor.xhtml", false, false, Tone.ERR));
            default -> List.of(
                    new TaskRow("r1", ts315, "Zarejestruj zwrotkę lub ponów wysyłkę", "Zawiadomienie o wyznaczeniu sędziego, wysłane 16.10.2026", null,
                            ts315.termin(), d(2026, 10, 16), "Doręczenia", "/rejestry/ekspedycja.xhtml", false, false, Tone.ERR),
                    new TaskRow("r2", ts301, "Sprawdź wpływ zażalenia", "Po wpływie zarejestruj w rejestrze zażaleń", null,
                            ts301.termin(), d(2026, 10, 28), "Rejestr zażaleń", "/rejestry/zazalenia.xhtml", false, false, Tone.WARN),
                    new TaskRow("r3", ts312, "Zakwalifikuj pismo DW 2026/04812", "Prawdopodobnie uzupełnienie braków, wpłynęło dziś 08:42", null,
                            braki312(), d(2026, 11, 3), "Kwalifikuj", "/sprawy/kolejka.xhtml", true, false, Tone.NEUTRAL),
                    new TaskRow("r4", ts318, "Przedstaw Prezesowi do wyznaczenia sędziego", "Karta sprawy kompletna", null,
                            DeadlineView.none(fmt.waitingText(6)), d(2026, 10, 28), "Przedstaw", "#", false, false, Tone.NEUTRAL),
                    new TaskRow("r5", null, "Kwalifikacja pisma DW 2026/04809", "Zadanie grupy: Wydział Weryfikacyjny · nikt nie przejął", null,
                            DeadlineView.none("czeka " + fmt.days(1)), d(2026, 11, 2), "Przejmij", "#", false, true, Tone.NEUTRAL));
        };
    }

    public List<TimelineEntry> notificationsFor(AppRole role) {
        return List.of(
                new TimelineEntry(dt(2026, 11, 3, 8, 42), "Wpływ pisma", "DW 2026/04812 od adw. Anny Testowej · Ts 312/26",
                        "/akta/podglad.xhtml?sygnatura=Ts%20312/26", true, true),
                new TimelineEntry(dt(2026, 11, 2, 15, 10), "Doręczono", "zarządzenie o usunięciu braków · Ts 312/26",
                        "/akta/podglad.xhtml?sygnatura=Ts%20312/26", false, true),
                new TimelineEntry(dt(2026, 11, 2, 11, 5), "Wpływ pisma", "DW 2026/04809 · zadanie grupy", null, false, true));
    }

    public List<JudgeLoad> judgeLoads() {
        return List.of(
                new JudgeLoad("sędzia TK Ewa Wzorcowa", "Ewa Wzorcowa", 3, 60, 1, null, "urlop 04–06.11.2026", Tone.WARN, false, true, null),
                new JudgeLoad("sędzia TK Adam Testowy", "Adam Testowy", 2, 40, 1, "(Ts 301/26, jutro)", null, Tone.NEUTRAL, true, true, null),
                new JudgeLoad("sędzia TK Piotr Fikcyjny", "Piotr Fikcyjny", 2, 40, 1, "(Ts 312/26)", null, Tone.NEUTRAL, false, true, null),
                new JudgeLoad("sędzia TK Maria Makietowa", "Maria Makietowa", 1, 20, 0, null, "wykonuje obowiązki Prezesa", Tone.NEUTRAL, false, true, null));
    }

    // ------------------------------------------------------------------ S-02 wyszukiwarka

    public List<CaseSummary> searchCases(String query) {
        if (query == null || query.isBlank() || query.contains("999")) {
            return List.of();
        }
        return List.of(caseBy("Ts 312/26"), caseBy("Ts 287/26"), caseBy("Ts 318/26"));
    }

    public List<SearchHit> searchHits(String query) {
        if (query == null || query.isBlank() || query.contains("999")) {
            return List.of();
        }
        String a = "/akta/podglad.xhtml?sygnatura=Ts%20312/26";
        return List.of(
                new SearchHit("Skarga konstytucyjna", a, sig("Ts 312/26"), "k. 1", d(2026, 10, 14), "pismo procesowe",
                        "…reprezentowany przez adw. Annę <mark>Testową</mark>, Kancelaria Adwokacka w Przykładowie…", null),
                new SearchHit("Pełnomocnictwo ogólne", a, sig("Ts 312/26"), "k. 23", d(2026, 10, 14), "załącznik",
                        "…udzielam adwokat Annie <mark>Testowej</mark> pełnomocnictwa ogólnego do reprezentowania mnie…", null),
                new SearchHit("Skarga konstytucyjna", "#", sig("Ts 287/26"), "k. 2", d(2026, 8, 12), "pismo procesowe",
                        "…Skarżąca Halina <mark>Testowa</mark>, reprezentowana przez adw. Annę <mark>Testową</mark>…", null),
                new SearchHit("Pełnomocnictwo szczególne", "#", sig("Ts 318/26"), "k. 9", d(2026, 10, 27), "załącznik",
                        "…radca prawny Oleńka <mark>Testowa</mark> do sporządzenia i wniesienia skargi konstytucyjnej…", "brak OCR na str. 2"));
    }

    // ------------------------------------------------------------------ S-03/S-04/S-06 wpływ

    public List<TimelineEntry> recentIntake() {
        return List.of(
                new TimelineEntry(dt(2026, 11, 2, 14, 20), "DW 2026/04811", "e-Doręczenia · Rada Gminy Przykładowo", null, false, true),
                new TimelineEntry(dt(2026, 11, 2, 11, 5), "DW 2026/04809", "poczta · Stefan Przykładny", null, false, true),
                new TimelineEntry(dt(2026, 10, 30, 9, 31), "DW 2026/04805", "osobiście · r.pr. Oleńka Testowa", null, false, true));
    }

    public List<IncomingLetterRow> incomingLetters() {
        return List.of(
                new IncomingLetterRow("DW 2026/04812", dt(2026, 11, 3, 8, 42), d(2026, 10, 31), IntakeChannel.POCZTA, "adw. Anna Testowa", null, null,
                        "podpowiedź: Ts 312/26", "w kolejce · 0 dni", Tone.NEUTRAL, Tone.NEUTRAL, true, 2, 1),
                new IncomingLetterRow("DW 2026/04811", dt(2026, 11, 2, 14, 20), d(2026, 11, 2), IntakeChannel.EDORECZENIA, "Rada Gminy Przykładowo",
                        "pismo do sprawy", sig("Tw 14/26"), null, "dołączone", Tone.OK, Tone.NEUTRAL, false, 4, 3),
                new IncomingLetterRow("DW 2026/04810", dt(2026, 11, 2, 10, 12), d(2026, 10, 30), IntakeChannel.POCZTA, "Zofia Zmyślona",
                        "pismo do sprawy", sig("Ts 315/26"), null, "dołączone", Tone.OK, Tone.NEUTRAL, false, 1, 0),
                new IncomingLetterRow("DW 2026/04809", dt(2026, 11, 2, 11, 5), d(2026, 10, 29), IntakeChannel.POCZTA, "Stefan Przykładny", null, null,
                        null, "w kolejce · 1 dzień", Tone.NEUTRAL, Tone.NEUTRAL, false, 6, 2),
                new IncomingLetterRow("DW 2026/04808", dt(2026, 10, 30, 13, 40), d(2026, 10, 30), IntakeChannel.EPUAP, "Lech Zmyślony", "pismo ogólne", null,
                        null, "odpowiedź do 30.11.2026", Tone.INFO, Tone.NEUTRAL, false, 1, 0),
                new IncomingLetterRow("DW 2026/04805", dt(2026, 10, 30, 9, 31), d(2026, 10, 30), IntakeChannel.OSOBISCIE, "r.pr. Oleńka Testowa", null, null,
                        null, "niezakwalifikowane · 4 dni", Tone.ERR, Tone.ERR, false, 3, 1),
                new IncomingLetterRow("DW 2026/04801", dt(2026, 10, 28, 10, 2), d(2026, 10, 27), IntakeChannel.POCZTA, "Przykładowa Spółka z o.o.",
                        "nowa skarga", sig("Ts 318/26"), null, "założono sprawę", Tone.OK, Tone.NEUTRAL, false, 12, 4));
    }

    public List<QueueItem> queue() {
        return List.of(
                new QueueItem("DW 2026/04805", "r.pr. Oleńka Testowa", IntakeChannel.OSOBISCIE, "4 dni w kolejce", Tone.ERR,
                        "Podpowiedź: Ts 318/26 (ten sam pełnomocnik)", false, false, Tone.ERR),
                new QueueItem("DW 2026/04806", "adw. Jan Makieta", IntakeChannel.POCZTA, "po terminie", Tone.WARN,
                        "Zażalenie w Ts 240/26 — odmowa jest już prawomocna", false, false, Tone.NEUTRAL),
                new QueueItem("DW 2026/04809", "Stefan Przykładny", IntakeChannel.POCZTA, "1 dzień", Tone.NEUTRAL,
                        "Podpowiedź: Ts 276/26 (ten sam skarżący)", false, false, Tone.NEUTRAL),
                new QueueItem("DW 2026/04812", "adw. Anna Testowa", IntakeChannel.POCZTA, "dziś 08:42", Tone.INFO,
                        "Uzupełnienie braków? Ts 312/26", true, true, Tone.NEUTRAL));
    }

    // ------------------------------------------------------------------ S-10 doręczenia

    public List<DeliveryRow> toSend() {
        return List.of(
                new DeliveryRow(sig("Ts 318/26"), "Zawiadomienie o wpisie do repertorium", "r.pr. Oleńka Testowa", "(pełnomocnik)", "e-Doręczenia",
                        d(2026, 11, 3), null, null, null, false, true, Tone.NEUTRAL),
                new DeliveryRow(sig("Ts 276/26"), "Zawiadomienie o terminie posiedzenia", "Stefan Przykładny", "(działa sam)", "poczta · polecony ZPO",
                        d(2026, 11, 2), null, null, null, false, false, Tone.NEUTRAL),
                new DeliveryRow(sig("Tw 14/26"), "Zawiadomienie o wyznaczeniu sędziego", "Rada Gminy Przykładowo", null, "e-Doręczenia",
                        d(2026, 11, 2), null, null, null, false, false, Tone.NEUTRAL));
    }

    public List<DeliveryRow> unconfirmed() {
        return List.of(
                new DeliveryRow(sig("Ts 315/26"), "Zawiadomienie o wyznaczeniu sędziego", "r.pr. Krzysztof Wzorowy", null, "poczta", d(2026, 10, 16), null,
                        dl(DeadlineType.ZWROTKA, DeadlineState.OVERDUE, null, null, "18 dni · ponad 14 dni", false), "Ponów wysyłkę", false, false, Tone.ERR),
                new DeliveryRow(sig("Ts 312/26"), "Zarządzenie o usunięciu braków", "adw. Anna Testowa", null, "e-Doręczenia", d(2026, 10, 28), null,
                        DeadlineView.none("6 dni"), "Zarejestruj zwrotkę", true, true, Tone.NEUTRAL));
    }

    public List<DeliveryRow> delivered() {
        return List.of(
                new DeliveryRow(sig("Ts 312/26"), "Zarządzenie o usunięciu braków", "adw. Anna Testowa", null, null, d(2026, 11, 2), "do rąk (e-Doręczenia)",
                        braki312(), null, false, false, Tone.NEUTRAL),
                new DeliveryRow(sig("Ts 301/26"), "Postanowienie o odmowie nadania dalszego biegu", "adw. Jan Makieta", null, null, d(2026, 10, 28),
                        "zastępcze (sekretariat kancelarii)", caseBy("Ts 301/26").termin(), null, false, false, Tone.NEUTRAL));
    }

    // ------------------------------------------------------------------ S-11/S-12/S-13 rejestry

    public List<AppealRow> appeals() {
        return List.of(
                new AppealRow(sig("Ts 301/26"), Set.of(CaseFlag.PILNA_ART_79), d(2026, 10, 28), null, "nie wpłynęło",
                        dl(DeadlineType.ZAZALENIE, DeadlineState.WARNING, d(2026, 11, 4), 86, fmt.untilText(d(2026, 11, 4), TODAY), true), null, null,
                        null, null, null, null, null, "Zarejestruj wpływ", "#", false, Tone.WARN),
                new AppealRow(sig("Ts 240/26"), Set.of(), d(2026, 8, 20), d(2026, 11, 2), "(DW 2026/04806)",
                        dl(DeadlineType.ZAZALENIE, DeadlineState.OVERDUE, null, null, "67 dni po upływie", false), null, null,
                        null, null, null, "ponowne zażalenie", Tone.NEUTRAL, "Przekaż Prezesowi", "/czynnosci/sklad.xhtml?stan=po", false, Tone.ERR),
                new AppealRow(sig("Ts 276/26"), Set.of(CaseFlag.ANONIMIZACJA_ART_80), d(2026, 10, 7), d(2026, 10, 13), null, null,
                        "w terminie (6 z 7 dni)", Tone.OK, "Wzorcowa, Testowy, Makietowa", "12.11.2026",
                        dl(DeadlineType.POSIEDZENIE, DeadlineState.NORMAL, d(2026, 11, 12), null, "12.11.2026 · " + fmt.daysText(9), false),
                        "Zażalenie w rozpoznaniu", Tone.INFO, "Akta", "/akta/podglad.xhtml?sygnatura=Ts%20276/26", true, Tone.NEUTRAL),
                new AppealRow(sig("Ts 240/26"), Set.of(), d(2026, 8, 20), d(2026, 8, 25), null, null, "w terminie (5 z 7 dni)", Tone.OK,
                        "Wzorcowa, Fikcyjny, Testowy", "10.09.2026", null, "nieuwzględnione 15.09.2026", Tone.OFF, "Postanowienie", "#", false, Tone.OFF));
    }

    public List<GeneralLetterRow> generalLetters() {
        return List.of(
                new GeneralLetterRow("DW 2026/04771", "Przykładowa Spółka z o.o.", "Pismo nazwane „skargą”, bez wymogów art. 53",
                        dl(DeadlineType.ODPOWIEDZ, DeadlineState.WARNING, d(2026, 11, 5), null, fmt.untilText(d(2026, 11, 5), TODAY), false),
                        "bez odpowiedzi", Tone.NEUTRAL, false, Tone.WARN),
                new GeneralLetterRow("DW 2026/04808", "Lech Zmyślony", "Prośba o informację, jak wnieść skargę konstytucyjną",
                        dl(DeadlineType.ODPOWIEDZ, DeadlineState.NORMAL, d(2026, 11, 30), null, fmt.untilText(d(2026, 11, 30), TODAY), false),
                        "projekt odpowiedzi", Tone.INFO, true, Tone.NEUTRAL),
                new GeneralLetterRow("DW 2026/04790", "Halina Testowa", "Pytanie o stan sprawy SK 41/26", null, "odpowiedziano 29.10.2026", Tone.OK,
                        false, Tone.OFF));
    }

    public List<AccessRequestRow> accessRequests() {
        return List.of(
                new AccessRequestRow("adw. Anna Testowa", "uczestnik · pełnomocnik", Tone.NEUTRAL, sig("Ts 312/26"), "całe akta jawne",
                        "05.11.2026, 10:00 · czytelnia 2", true, "12 stron · 12,00 zł", "nie dotyczy", Tone.NEUTRAL, false, false),
                new AccessRequestRow("Zofia Zmyślona", "osoba trzecia", Tone.NOTE, sig("Ts 287/26"), "postanowienie o nadaniu biegu", "nie wyznaczono",
                        false, "4 strony · 4,00 zł", "w przygotowaniu", Tone.WARN, true, true),
                new AccessRequestRow("Robert Fikcyjny", "uczestnik · skarżący", Tone.NEUTRAL, sig("Ts 301/26"), "kopia postanowienia o odmowie",
                        "kopia elektroniczna", true, "bez opłaty", "nie dotyczy", Tone.NEUTRAL, false, false));
    }

    public List<PublicationItem> publications() {
        return List.of(
                new PublicationItem(sig("Ts 276/26"), null, null, true, "Postanowienie o odmowie · 30.09.2026", "5 zamian · wersja zanonimizowana v1", 5, true),
                new PublicationItem(sig("Ts 287/26"), "nadano bieg", Tone.OK, false, "Postanowienie · 24.10.2026", "3 zamiany", 3, false),
                new PublicationItem(sig("Ts 240/26"), "odmowa prawomocna", Tone.OFF, false, "Postanowienie · 15.08.2026", "4 zamiany", 4, false));
    }

    // ------------------------------------------------------------------ S-14 akta Ts 312/26

    public List<RecordPage> recordPages(String sygnatura) {
        return List.of(
                new RecordPage("k1", "k. 1–14", d(2026, 10, 14), "Skarga konstytucyjna", DocumentKind.PISMO_PROCESOWE,
                        "pismo procesowe · podpis odręczny (skan) · 3 zaznaczenia", false, true, false),
                new RecordPage("k15", "k. 15–22", d(2026, 10, 14), "Wyrok SO w Przykładowie, II Ca 123/26", DocumentKind.ZALACZNIK,
                        "załącznik · ostateczne orzeczenie", false, false, false),
                new RecordPage("k23", "k. 23", d(2026, 10, 14), "Pełnomocnictwo ogólne", DocumentKind.ZALACZNIK, "załącznik · podpis odręczny", false, false, false),
                new RecordPage("k24", "k. 24", d(2026, 10, 20), "Zarządzenie o wyznaczeniu sędziego", DocumentKind.CZYNNOSC,
                        "czynność · podpis kwalifikowany ważny", false, false, false),
                new RecordPage("k25", "k. 25–26", d(2026, 10, 28), "Zarządzenie o usunięciu braków", DocumentKind.CZYNNOSC, "czynność · podpisane, wysłane",
                        false, false, false),
                new RecordPage("k27", "k. 27", d(2026, 11, 2), "Potwierdzenie odbioru", DocumentKind.DORECZENIE, "doręczenie · start terminu na braki",
                        false, false, true),
                new RecordPage("k28", "k. 28–29", d(2026, 11, 3), "Pismo uzupełniające", DocumentKind.PISMO_PROCESOWE,
                        "DW 2026/04812 · z pełnomocnictwem szczególnym", true, false, false),
                new RecordPage("kN", "—", null, "Załącznik z klauzulą „zastrzeżone”", DocumentKind.NIEJAWNY, "dokument niejawny · tylko metadane",
                        false, false, false));
    }

    @Override
    public List<pl.ejtechnology.tk.eSkarga.web.model.DraftRow> drafts(String sygnatura) {
        return List.of();   // w demo podpis to makieta S-16 (?stan=)
    }

    public List<TimelineEntry> timeline(String sygnatura) {
        return List.of(
                new TimelineEntry(dt(2026, 11, 3, 8, 42), "Biuro podawcze", "zarejestrowało pismo DW 2026/04812 (k. 28–29)", "#", true, true),
                new TimelineEntry(dt(2026, 11, 2, 10, 24), "System", "zarejestrował doręczenie zarządzenia (k. 27) · start terminu", "#k27", false, true),
                new TimelineEntry(dt(2026, 10, 28, 14, 12), "sędzia TK Piotr Fikcyjny", "podpisał zarządzenie o usunięciu braków", "#", false, true),
                new TimelineEntry(dt(2026, 10, 27, 11, 40), "Tomasz Próbny", "przygotował projekt zarządzenia", "#", false, true),
                new TimelineEntry(dt(2026, 10, 20, 9, 15), "Prezes TK", "wyznaczył sędziego (k. 24)", "#", false, true));
    }

    public List<ProcessStep> processSteps(String sygnatura) {
        return List.of(
                new ProcessStep("Wpływ", d(2026, 10, 14), null, true, false, null, 1),
                new ProcessStep("Wyznaczenie sędziego", d(2026, 10, 20), null, true, false, null, 2),
                new ProcessStep("Wstępna kontrola", d(2026, 10, 20), "od", true, false, null, 3),
                new ProcessStep("Wezwanie do usunięcia braków", null, null, false, true, "Wpłynęło pismo uzupełniające (k. 28–29) — do oceny", 4),
                new ProcessStep("Nadanie dalszego biegu albo odmowa", null, null, false, false, null, 5));
    }

    public List<pl.ejtechnology.tk.eSkarga.web.model.NoteRow> notes(String sygnatura) {
        return List.of(
                new pl.ejtechnology.tk.eSkarga.web.model.NoteRow(1L, "Notatka asystenta do sędziego", "Pełnomocnictwo szczególne (k. 2) nie obejmuje sporządzenia skargi – proponuję wezwanie do usunięcia braku.", "Tomasz Próbny", dt(2026, 10, 31, 14, 5)),
                new pl.ejtechnology.tk.eSkarga.web.model.NoteRow(2L, "Zaznaczenia w skardze", "k. 1, k. 3, k. 9 – wzorzec kontroli wskazany tylko ogólnie.", "Tomasz Próbny", dt(2026, 10, 27, 11, 50)));
    }

    public boolean canReadNotes(String sygnatura) {
        return true;   // w demo zakładka pokazuje dane przykładowe; dostęp w trybie OIDC rozstrzyga NoteService (FR-24)
    }

    public List<ChecklistItem> checklist(String sygnatura) {
        return List.of(
                new ChecklistItem("Sporządzenie przez adwokata lub radcę prawnego", "art. 44 ust. 1", ChecklistItem.State.SPELNIONY, "adw. Anna Testowa", false, "k. 1"),
                new ChecklistItem("Pełnomocnictwo szczególne do sporządzenia skargi", "art. 53 ust. 2 pkt 3", ChecklistItem.State.BRAK,
                        "pełnomocnictwo z 30.10.2026 udzielono po wniesieniu skargi (13.10.2026); nie obejmuje jej sporządzenia", true, "k. 23"),
                new ChecklistItem("Wskazanie przepisu i wzorca kontroli", "art. 53 ust. 1 pkt 1", ChecklistItem.State.SPELNIONY, null, false, "k. 1"),
                new ChecklistItem("Uzasadnienie zarzutu niezgodności", "art. 53 ust. 1 pkt 3", ChecklistItem.State.DO_SPRAWDZENIA, "do sprawdzenia, nie wstawiono", false, "k. 2–9"),
                new ChecklistItem("Ostateczne orzeczenie i dowód wyczerpania drogi prawnej", "art. 53 ust. 1 pkt 4, ust. 2 pkt 1", ChecklistItem.State.SPELNIONY,
                        "wyrok SO II Ca 123/26", false, "k. 15–22"),
                new ChecklistItem("Termin 3 miesięcy od doręczenia orzeczenia", "art. 77 ust. 1", ChecklistItem.State.SPELNIONY,
                        "doręczono 14.07.2026, wniesiono 13.10.2026 (pomocniczo)", false, null));
    }

    public List<DeadlineView> deadlines(String sygnatura) {
        return List.of(
                braki312(),
                dl(DeadlineType.ART_77, DeadlineState.AUXILIARY, d(2026, 10, 14), 100, "upłynął 14.10.2026 · wniesiono 13.10.2026 · " + fmt.msg("deadline.auxiliaryNote"), true));
    }

    // ------------------------------------------------------------------ S-19/S-20 nadzór

    public List<HBar> clerkLoads() {
        return List.of(new HBar("Marek Przykładowski", 14, 85, true));
    }

    public List<HBar> judgeBars() {
        List<HBar> out = new ArrayList<>();
        for (JudgeLoad j : judgeLoads()) {
            out.add(new HBar(j.nazwisko(), j.sprawy(), j.percent(), false));
        }
        return out;
    }

    public List<HBar> outcomes() {
        return List.of(
                new HBar("Odmowa — oczywista bezzasadność (pkt 2)", 112, 100, false),
                new HBar("Odmowa — wymogi formalne (pkt 1)", 96, 86, false),
                new HBar("Nadanie dalszego biegu", 41, 37, false),
                new HBar("Odmowa — braki nieusunięte (pkt 3)", 28, 25, false),
                new HBar("Umorzenie", 12, 11, false));
    }

    public int[] monthlyIntake() {
        return new int[] {28, 31, 35, 30, 33, 36, 29, 27, 34, 35};
    }

    public int[] monthlyDisposed() {
        return new int[] {22, 27, 30, 31, 29, 33, 30, 24, 31, 32};
    }

    public List<ArchiveRow> archive(boolean pdfaError) {
        return List.of(
                new ArchiveRow(sig("Ts 240/26"), "odmowa prawomocna · 15.09.2026", "B10", "10 lat · do 2036",
                        pdfaError ? "brak PDF/A: k. 31" : "komplet · 34 karty PDF/A", pdfaError ? Tone.ERR : Tone.OK, true, true, 34),
                new ArchiveRow(sig("Ts 287/26"), "nadano bieg · 24.10.2026", "A", "wieczyście", "akta w SK 41/26 — przekazanie po jej zakończeniu",
                        Tone.OFF, false, false, 0));
    }

    // ------------------------------------------------------------------ S-22 – S-25 administracja

    public List<UserRow> users() {
        return List.of(
                new UserRow("Karolina Wzorek", "Biuro Podawcze", "biuro podawcze", dt(2026, 11, 3, 7, 55), null, false, null, false),
                new UserRow("Marek Przykładowski", "Wydział Weryfikacyjny", "referent", dt(2026, 11, 3, 8, 1), "sprawy Ts: wszystkie bez referenta (14)", true, null, true),
                new UserRow("Ewa Szablon", "Wydział Weryfikacyjny", "kierownik", dt(2026, 11, 3, 8, 30), null, false, null, false),
                new UserRow("Tomasz Próbny", "Gabinet sędziego", "asystent", dt(2026, 11, 3, 8, 12), "asystent sędziego TK Piotra Fikcyjnego", true, null, false),
                new UserRow("sędzia TK Piotr Fikcyjny", "Trybunał", "sędzia", dt(2026, 11, 2, 17, 10), null, false, null, false),
                new UserRow("sędzia TK Ewa Wzorcowa", "Trybunał", "sędzia", dt(2026, 11, 2, 16, 41), null, false, null, false),
                new UserRow("sędzia TK Adam Testowy", "Trybunał", "sędzia", dt(2026, 11, 3, 8, 40), null, false, null, false),
                new UserRow("sędzia TK Maria Makietowa", "Trybunał", "sędzia", dt(2026, 11, 3, 9, 18), null, false, "upoważnienie: obowiązki Prezesa TK do 31.12.2026", false),
                new UserRow("Jan Archiwalny", "Archiwum", "archiwista", dt(2026, 10, 30, 13, 2), null, false, null, false),
                new UserRow("Anna Admin", "Biuro Informatyki", "administrator", dt(2026, 11, 3, 7, 40), null, false, null, false));
    }

    public List<DelegationRow> delegations() {
        return List.of(
                new DelegationRow("u1", "sędzia TK Maria Makietowa", "wykonywanie obowiązków Prezesa TK", d(2026, 10, 30), d(2026, 12, 31),
                        "zarządzenie nr 12/2026 (PDF)", "Anna Admin · 30.10.2026", "aktywne · jeszcze 58 dni", Tone.OK, "check", true, Tone.NEUTRAL),
                new DelegationRow("u2", "sędzia TK Ewa Wzorcowa", "wykonywanie obowiązków Prezesa TK", d(2026, 12, 1), d(2026, 12, 15),
                        "zarządzenie nr 13/2026 (PDF)", "Anna Admin · 02.11.2026", "przyszłe · nakłada się", Tone.WARN, "alert-triangle", false, Tone.WARN),
                new DelegationRow("u3", "sędzia TK Adam Testowy", "podpisywanie zarządzeń o wyznaczeniu sędziego", d(2026, 10, 1), d(2026, 11, 9),
                        "zarządzenie nr 9/2026 (PDF)", "Anna Admin · 30.09.2026", "aktywne · wygasa za 6 dni", Tone.WARN, "timer", false, Tone.WARN),
                new DelegationRow("u4", "sędzia TK Piotr Fikcyjny", "wykonywanie obowiązków Prezesa TK", d(2026, 7, 1), d(2026, 7, 31),
                        "zarządzenie nr 6/2026 (PDF)", "Anna Admin · 28.06.2026", "wygasłe", Tone.OFF, null, false, Tone.OFF),
                new DelegationRow("u5", "Ewa Szablon", "akceptacja korekt repertorium w zastępstwie", d(2026, 9, 15), d(2026, 9, 30),
                        "polecenie służbowe (PDF)", "Anna Admin · 14.09.2026", "odwołane 20.09.2026", Tone.OFF, null, false, Tone.OFF));
    }

    public List<TemplateRow> templates() {
        return List.of(
                new TemplateRow(1L, "ZARZ_BRAKI", "Zarządzenie o usunięciu braków", "zarządzenie", "v4", "12.10.2026 · Anna Admin", "obowiązuje", Tone.OK, true, 12),
                new TemplateRow(2L, "POST_ODMOWA", "Postanowienie o odmowie nadania dalszego biegu", "postanowienie", "v3", "01.09.2026", "obowiązuje", Tone.OK, false, 7),
                new TemplateRow(3L, "POST_NADANIE_BIEGU", "Postanowienie o nadaniu dalszego biegu", "postanowienie", "v2", "01.09.2026", "obowiązuje", Tone.OK, false, 5),
                new TemplateRow(4L, "ZARZ_WYZNACZENIE_SEDZIEGO", "Zarządzenie o wyznaczeniu sędziego", "zarządzenie", "v2", "30.10.2026", "obowiązuje", Tone.OK, false, 31),
                new TemplateRow(5L, "POUCZENIE", "Pouczenie o wymogach skargi konstytucyjnej", "pismo ogólne", "v5", "15.10.2026", "obowiązuje", Tone.OK, false, 9),
                new TemplateRow(6L, "ZARZ_BRAKI", "Zarządzenie o usunięciu braków", "zarządzenie", "v3", "02.03.2026", "archiwalna", Tone.OFF, false, 12));
    }

    public List<pl.ejtechnology.tk.eSkarga.web.model.ScanRow> scansOf(String registerNo) {
        return List.of();
    }

    @Override
    public java.util.Map<String, Integer> dictionaryCounts() {
        return java.util.Map.of("sady", 312, "organy", 48, "typyPism", 27, "wzorce", 64);
    }

    @Override
    public List<DictionaryRow> reviewStandards() {
        return List.of(
                new DictionaryRow("K-002", "art. 2 Konstytucji", "demokratyczne państwo prawne", 118, true, false),
                new DictionaryRow("K-032-1", "art. 32 ust. 1 Konstytucji", "równość wobec prawa", 74, true, false),
                new DictionaryRow("K-045-1", "art. 45 ust. 1 Konstytucji", "prawo do sądu", 136, true, true),
                new DictionaryRow("K-078", "art. 78 Konstytucji", "prawo do zaskarżenia orzeczeń", 89, true, false),
                new DictionaryRow("K-176-X", "art. 176 ust. 1 Konstytucji (stary kod)", "zastąpiona przez K-176-1", 0, false, false));
    }

    public List<AuditRow> audit() {
        return List.of(
                new AuditRow(184312, dt(2026, 11, 3, 9, 20).withSecond(14), "Maria Makietowa", "Prezes TK (upoważnienie)", "podpis dokumentu", "Ts 318/26",
                        "zarządzenie · k. 12", "10.12.4.31", "3f9a…c41e", "b07d…92aa", "upoważnienie z 30.10.2026", "mmakietowa", true),
                new AuditRow(184311, dt(2026, 11, 3, 9, 15).withSecond(2), "Ewa Szablon", "kierownik", "zatwierdzenie korekty", "Ts 318/26",
                        "wpis repertorium v2", "10.12.4.22", "b07d…92aa", "77c1…0e3f", null, "eszablon", false),
                new AuditRow(184310, dt(2026, 11, 3, 8, 58).withSecond(40), "Tomasz Próbny", "asystent", "zapis projektu", "Ts 312/26",
                        "projekt postanowienia v2", "10.12.5.17", "77c1…0e3f", "a9e2…5d10", null, "tprobny", false),
                new AuditRow(184309, dt(2026, 11, 3, 8, 42).withSecond(11), "Karolina Wzorek", "biuro podawcze", "rejestracja wpływu", "Ts 312/26",
                        "DW 2026/04812", "10.12.2.5", "a9e2…5d10", "41bb…c0a7", null, "kwzorek", false),
                new AuditRow(184308, dt(2026, 11, 3, 8, 12).withSecond(55), "Tomasz Próbny", "asystent", "wyświetlenie akt", "Ts 312/26", "k. 1–14",
                        "10.12.5.17", "41bb…c0a7", "0d5e…88f2", null, "tprobny", false),
                new AuditRow(184307, dt(2026, 11, 3, 7, 40).withSecond(3), "Anna Admin", "administrator", "logowanie", "—", "sesja z MFA", "10.12.9.2",
                        "0d5e…88f2", "ee01…3b9c", null, "aadmin", false));
    }

    /** Kalendarz dni wolnych 2026 z dniem wolnym w Trybunale za 15.08.2026 (sobota). */
    @Override
    public List<CalendarMonth> calendar(int year) {
        return pl.ejtechnology.tk.eSkarga.web.CalendarBuilder.build(year, Map.of(d(year, 11, 10), "Dzień wolny za 15.08." + year + " (sobota)"));
    }

    public Set<LocalDate> extraDaysOff(int year) {
        return Set.of(LocalDate.of(year, Month.NOVEMBER, 10));
    }
}
