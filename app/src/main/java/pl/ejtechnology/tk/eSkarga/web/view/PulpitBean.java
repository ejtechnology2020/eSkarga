package pl.ejtechnology.tk.eSkarga.web.view;

import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.HBar;
import pl.ejtechnology.tk.eSkarga.web.model.KpiTile;
import pl.ejtechnology.tk.eSkarga.web.model.TaskRow;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/**
 * S-01 Pulpit zadań (UC-18, FR-33). Zadania z {@code TaskService} filtrowane przez {@code CaseAccessPolicy};
 * Tasklist Operaton nie jest używany. Układ nie zmienia się między rolami – różnią się treść i akcje.
 */
@Named("pulpit")
@ViewScoped
public class PulpitBean implements Serializable {

    @Inject
    private CurrentUser currentUser;
    @Inject
    private ViewData data;

    private AppRole role;
    private List<KpiTile> kpis;
    private List<TaskRow> tasks;
    private List<TimelineEntry> notifications;
    @Inject
    private pl.ejtechnology.tk.eSkarga.web.AppClock clock;
    /** Filtr listy zadań (chipy): all, terminy (termin w 7 dni), grupowe (do przejęcia), pilne (art. 79). */
    private String filter = "all";
    private boolean comfy;

    @PostConstruct
    void init() {
        role = currentUser.getRole();
        kpis = data.kpisFor(role);
        tasks = data.tasksFor(role);
        notifications = data.notificationsFor(role);
    }

    public String getGreeting() {
        String name = currentUser.getDisplayName().replace("sędzia TK ", "");
        String first = name.split("\\s+")[0];
        return switch (role) {
            case TK_PREZES -> "Dzień dobry. Masz " + tasksLabel(tasks.size()) + ".";
            default -> "Dzień dobry, " + vocative(first) + ". Masz " + tasksLabel(tasks.size()) + ".";
        };
    }

    /** Wołacz imienia na potrzeby powitania (dane fikcyjne; reguła uproszczona). */
    public static String vocative(String first) {
        if (first.endsWith("ek")) {
            return first.substring(0, first.length() - 2) + "ku";
        }
        if (first.endsWith("sz")) {
            return first + "u";
        }
        if (first.endsWith("a")) {
            return first.substring(0, first.length() - 1) + "o";
        }
        return first + "ie";
    }

    public static String tasksLabel(int n) {
        if (n == 1) {
            return "1 zadanie";
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            return n + " zadania";
        }
        return n + " zadań";
    }

    public String getSubtitle() {
        return switch (role) {
            case TK_ASYSTENT, TK_SEDZIA -> "Sprawy sędziego TK Piotra Fikcyjnego. Najbliższy termin: braki w Ts 312/26 do 09.11.2026.";
            case TK_PREZES -> "2 jako Prezes TK (z upoważnienia), 1 jako sędzia w składzie.";
            case TK_BIURO_PODAWCZE -> "6 pism czeka na rejestrację, w tym 2 z e-Doręczeń.";
            case TK_KIEROWNIK -> "5 spraw wymaga uwagi. 1 korekta czeka na akceptację.";
            default -> "1 termin upływa jutro, 1 zadanie jest po terminie.";
        };
    }

    public AppRole getRole() {
        return role;
    }

    /** Liczba pism w kolejce kwalifikacji (przycisk „Otwórz kolejkę pism”) – z danych, nie z makiety. */
    public int getQueueSize() {
        return data.queue().size();
    }

    /** Sprawy, w których zalogowany jest sędzią (przycisk „Sprawy sędziego”). */
    public long getJudgeCases() {
        String login = currentUser.getLogin();
        return data.cases().stream().filter(c -> login != null && login.equals(c.sedzia())).count();
    }

    public boolean isRole(String code) {
        return role == AppRole.fromShortCode(code);
    }

    public boolean isJudgeTeam() {
        return role == AppRole.TK_ASYSTENT || role == AppRole.TK_SEDZIA;
    }

    public List<KpiTile> getKpis() {
        return kpis;
    }

    public List<TaskRow> getTasks() {
        return tasks.stream().filter(t -> matches(t, filter)).toList();
    }

    /** Liczba zadań dla chipa filtra – z danych, nie z makiety. */
    public long count(String f) {
        return tasks.stream().filter(t -> matches(t, f)).count();
    }

    private boolean matches(TaskRow t, String f) {
        return switch (f == null ? "all" : f) {
            case "terminy" -> t.termin() != null && t.termin().end() != null && !t.termin().end().isAfter(clock.today().plusDays(7));
            case "grupowe" -> t.grupowe();
            case "pilne" -> t.opis() != null && t.opis().contains("pilna");
            case "wyznacz" -> "Wyznacz sędziego".equals(t.akcja());
            case "podpis" -> t.akcjaUrl() != null && t.akcjaUrl().contains("/czynnosci/podpis");
            default -> true;
        };
    }

    public List<TimelineEntry> getNotifications() {
        return notifications;
    }

    public List<HBar> getJudgeBars() {
        return data.judgeBars();
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public boolean isComfy() {
        return comfy;
    }

    public void setComfy(boolean comfy) {
        this.comfy = comfy;
    }

    public boolean isShowRoleColumn() {
        return role == AppRole.TK_PREZES;
    }
}
