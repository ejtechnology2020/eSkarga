package pl.ejtechnology.tk.eSkarga.process;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.task.TaskQuery;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Deadline;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseFileService;
import pl.ejtechnology.tk.eSkarga.domain.service.DeliveryService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Pulpit zadań (T-022, UC-18, FR-33): zadania użytkownika z {@link TaskService} (przydzielone i grupowe), każde przefiltrowane
 * przez {@link CaseAccessPolicy}, z biegnącym terminem sprawy i liczbą dni. Tasklist Operaton nie jest używany.
 */
@Stateless
public class TaskBoardService {

    /** Pozycja pulpitu. */
    public record Item(String taskId, String taskKey, String name, long caseId, DocketNumber docket, CaseStatus status, Instant createdAt,
                       LocalDate deadlineEnd, Long daysLeft, String assignee, boolean group, boolean urgent) {
        /** Termin ≤ 2 dni albo po terminie – oznaczany tekstem i kolorem (UC-18). */
        public boolean nearDeadline() {
            return daysLeft != null && daysLeft <= 2;
        }
    }

    /** Role aplikacji → grupy kandydatów w modelu BPMN. */
    public static final Map<AppRole, String> CANDIDATE_GROUPS = new EnumMap<>(Map.of(
            AppRole.TK_BIURO_PODAWCZE, "tk-biuro-podawcze",
            AppRole.TK_REFERENT, "tk-referenci",
            AppRole.TK_PREZES, "tk-prezes",
            AppRole.TK_SEDZIA, "tk-sklad"));

    @Inject
    TaskService taskService;
    @Inject
    CaseFileService cases;
    @Inject
    DeliveryService deliveries;
    @Inject
    CaseAccessService access;
    @Inject
    AppClock clock;

    public static Set<String> groupsFor(Set<AppRole> roles) {
        Set<String> groups = new LinkedHashSet<>();
        for (AppRole r : roles) {
            String g = CANDIDATE_GROUPS.get(r);
            if (g != null) {
                groups.add(g);
            }
        }
        return groups;
    }

    /** Zadania użytkownika posortowane po terminie (najbliższy pierwszy), bez spraw, do których nie ma dostępu. */
    public List<Item> tasksFor(String userId, Set<AppRole> roles) {
        Set<String> groups = groupsFor(roles);
        TaskQuery q = taskService.createTaskQuery().active().or().taskAssignee(userId);
        if (!groups.isEmpty()) {
            q = q.taskCandidateGroupIn(new ArrayList<>(groups));
        }
        List<Task> tasks = q.endOr().list();
        List<Item> items = new ArrayList<>();
        for (Task t : tasks) {
            // zadanie grupowe już przydzielone komuś innemu znika z pulpitu pozostałych kandydatów
            if (t.getAssignee() != null && !t.getAssignee().equals(userId)) {
                continue;
            }
            toItem(t, userId, roles).ifPresent(items::add);
        }
        items.sort(Comparator.comparing((Item i) -> i.deadlineEnd() == null ? LocalDate.MAX : i.deadlineEnd()).thenComparing(Item::createdAt));
        return items;
    }

    /** Widok kierownika: wszystkie zadania z terminem, który minął albo mija w ciągu {@code days} dni. */
    public List<Item> dueWithin(int days, String userId, Set<AppRole> roles) {
        if (!roles.contains(AppRole.TK_KIEROWNIK) && !roles.contains(AppRole.TK_PREZES)) {
            return List.of();
        }
        List<Item> items = new ArrayList<>();
        for (Task t : taskService.createTaskQuery().active().list()) {
            toItem(t, userId, roles).filter(i -> i.daysLeft() != null && i.daysLeft() <= days).ifPresent(items::add);
        }
        items.sort(Comparator.comparing(Item::deadlineEnd));
        return items;
    }

    private Optional<Item> toItem(Task t, String userId, Set<AppRole> roles) {
        Object caseIdVar = taskService.getVariable(t.getId(), ProcessVariables.CASE_ID);
        if (!(caseIdVar instanceof Number n)) {
            return Optional.empty();
        }
        CaseFile cf;
        try {
            cf = cases.require(n.longValue());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!CaseAccessPolicy.canReadCase(userId, roles, access.assignment(cf)) && !userId.equals(t.getAssignee())) {
            return Optional.empty();
        }
        LocalDate end = deliveries.runningDeadlines(cf).stream().map(Deadline::getEndsOn).min(Comparator.naturalOrder()).orElse(null);
        Long days = end == null ? null : ChronoUnit.DAYS.between(clock.today(), end);
        return Optional.of(new Item(t.getId(), t.getTaskDefinitionKey(), t.getName(), cf.getId(), cf.getDocket(), cf.current().getStatus(),
                t.getCreateTime() == null ? clock.instant() : t.getCreateTime().toInstant(), end, days, t.getAssignee(), t.getAssignee() == null,
                cf.current().isFlagUrgent()));
    }
}
