package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;

/** Menu boczne zależne od roli: sześć stałych grup, widoczne tylko dozwolone pozycje, liczniki zadań. */
@Named("menu")
@RequestScoped
public class MenuBean implements Serializable {

    /** Pozycja menu. */
    public record Item(Screen screen, String label, String href, boolean active, String counter) implements Serializable {
    }

    /** Grupa menu z pozycjami. */
    public record Group(Screen.Group group, String label, String icon, List<Item> items, boolean active, String firstHref)
            implements Serializable {
        public String id() {
            return "nav-" + group.name().toLowerCase();
        }
    }

    @Inject
    private CurrentUser currentUser;
    @Inject
    private DemoDataset demo;
    @Inject
    private Fmt fmt;

    private List<Group> groups;
    private Screen current;

    @PostConstruct
    void init() {
        FacesContext fc = FacesContext.getCurrentInstance();
        String viewId = fc != null && fc.getViewRoot() != null ? fc.getViewRoot().getViewId() : null;
        String ctx = fc != null ? fc.getExternalContext().getRequestContextPath() : "";
        current = viewId == null ? null : Screen.byPath(viewId);
        AppRole role = currentUser.getRole();
        Map<Screen.Group, List<Item>> byGroup = new EnumMap<>(Screen.Group.class);
        for (Screen s : Screen.values()) {
            if (currentUser.getRoles().stream().noneMatch(s::visibleFor)) {
                continue;
            }
            Integer cnt = demo.menuCounter(s, role);
            byGroup.computeIfAbsent(s.group(), g -> new ArrayList<>())
                    .add(new Item(s, fmt.msg(s.messageKey()), ctx + s.path(), s == current, cnt == null ? null : cnt.toString()));
        }
        groups = new ArrayList<>();
        for (Screen.Group g : Screen.Group.values()) {
            List<Item> items = byGroup.get(g);
            if (items == null || items.isEmpty()) {
                continue;
            }
            boolean active = items.stream().anyMatch(Item::active);
            groups.add(new Group(g, fmt.msg("menu.group." + g.name()), g.icon(), items, active, items.get(0).href()));
        }
    }

    public List<Group> getGroups() {
        return groups;
    }

    public Screen getCurrent() {
        return current;
    }

    public String getCurrentGroupLabel() {
        return current == null ? "" : fmt.msg("menu.group." + current.group().name());
    }

    public String getCurrentLabel() {
        return current == null ? "" : fmt.msg(current.messageKey());
    }
}
