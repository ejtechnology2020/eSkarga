package pl.ejtechnology.tk.eSkarga.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import pl.ejtechnology.tk.eSkarga.security.AppRole;
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
 * Dane do widoków (ADR 0017): jedna umowa dla beanów i Facelets ({@code #{dane.…}}), dwie implementacje – dane przykładowe
 * (tryb demo, ADR 0004) i usługi domenowe na JPA/Operaton. Beany nie wiedzą, skąd pochodzą dane.
 */
public interface ViewData {

    CaseSummary caseBy(String sygnatura);

    List<CaseSummary> cases();

    Integer menuCounter(Screen s, AppRole role);

    List<KpiTile> kpisFor(AppRole role);

    List<TaskRow> tasksFor(AppRole role);

    List<TimelineEntry> notificationsFor(AppRole role);

    List<JudgeLoad> judgeLoads();

    List<CaseSummary> searchCases(String query);

    List<SearchHit> searchHits(String query);

    List<TimelineEntry> recentIntake();

    List<IncomingLetterRow> incomingLetters();

    List<QueueItem> queue();

    List<DeliveryRow> toSend();

    List<DeliveryRow> unconfirmed();

    List<DeliveryRow> delivered();

    List<AppealRow> appeals();

    List<GeneralLetterRow> generalLetters();

    List<AccessRequestRow> accessRequests();

    List<PublicationItem> publications();

    List<RecordPage> recordPages(String sygnatura);

    List<TimelineEntry> timeline(String sygnatura);

    List<ProcessStep> processSteps(String sygnatura);

    List<ChecklistItem> checklist(String sygnatura);

    List<DeadlineView> deadlines(String sygnatura);

    List<HBar> clerkLoads();

    List<HBar> judgeBars();

    List<HBar> outcomes();

    int[] monthlyIntake();

    int[] monthlyDisposed();

    List<ArchiveRow> archive(boolean pdfaError);

    List<UserRow> users();

    List<DelegationRow> delegations();

    List<TemplateRow> templates();

    List<DictionaryRow> reviewStandards();

    List<AuditRow> audit();

    List<CalendarMonth> calendar(int year);

    Set<LocalDate> extraDaysOff(int year);
}
