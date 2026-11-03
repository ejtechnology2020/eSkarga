package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Ikony liniowe (stroke) jako ścieżki SVG z makiet. Każda ikona w UI ma etykietę albo podpowiedź tekstową;
 * sama ikona nigdy nie niesie znaczenia (WCAG 2.1 AA).
 */
@Named("icons")
@ApplicationScoped
public class Icons implements Serializable {

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("home", "M3 11 12 4l9 7v9h-6v-6H9v6H3z"),
            Map.entry("inbox", "M4 13h4l2 3h4l2-3h4M4 13l2-8h12l2 8v6H4z"),
            Map.entry("folder", "M3 6h6l2 2h10v11H3z"),
            Map.entry("pen", "M4 20h4L19 9l-4-4L4 16zM14 6l4 4"),
            Map.entry("book", "M5 4h12v16H5zM9 4v16"),
            Map.entry("chart", "M4 20V10M10 20V4M16 20v-7M22 20H2"),
            Map.entry("search", "M4 11a7 7 0 1 0 14 0 7 7 0 1 0-14 0 M20 20l-4-4"),
            Map.entry("bell", "M6 16V11a6 6 0 1 1 12 0v5l2 2H4z M10 21h4"),
            Map.entry("logout", "M15 4h4v16h-4 M10 8l-4 4 4 4M6 12h10"),
            Map.entry("copy", "M9 9h11v11H9z M5 15V5a1 1 0 0 1 1-1h10"),
            Map.entry("check", "M4 12l5 5L20 6"),
            Map.entry("x", "M6 6l12 12M18 6 6 18"),
            Map.entry("minus", "M5 12h14"),
            Map.entry("clock", "M12 4a8 8 0 1 0 0 16 8 8 0 1 0 0-16 M12 8v4l3 2"),
            Map.entry("timer", "M12 5a8 8 0 1 0 0 16 8 8 0 1 0 0-16 M12 9v4l2.5 2M9 2h6"),
            Map.entry("alert-circle", "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18 M12 7v6M12 16.5v.5"),
            Map.entry("alert-triangle", "M12 3 2 20h20L12 3z M12 10v4M12 17v.5"),
            Map.entry("info", "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18 M12 11v5M12 8v.5"),
            Map.entry("pause", "M9 6v12M15 6v12"),
            Map.entry("list", "M4 7h16M4 12h16M4 17h10"),
            Map.entry("archive", "M4 5h16v14H4z M4 9h16"),
            Map.entry("lock", "M5 11h14v10H5z M8 11V7a4 4 0 0 1 8 0v4"),
            Map.entry("flag", "M5 21V4h12l-2 4 2 4H5"),
            Map.entry("user-off", "M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8 M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6M3 3l18 18"),
            Map.entry("shield", "M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z"),
            Map.entry("shield-check", "M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z M9 12l2 2 4-4"),
            Map.entry("arrow-right", "M5 12h14M13 6l6 6-6 6"),
            Map.entry("file-text", "M6 3h8l4 4v14H6z M14 3v4h4 M9 12h6M9 16h6"),
            Map.entry("paperclip", "M8 12l6-6a3 3 0 1 1 4 4l-8 8a5 5 0 0 1-7-7l7-7"),
            Map.entry("mail", "M3 6h18v12H3z M3 6l9 7 9-7"),
            Map.entry("note", "M5 4h14v12l-4 4H5z M15 20v-4h4"),
            Map.entry("zoom-in", "M4 11a7 7 0 1 0 14 0 7 7 0 1 0-14 0 M20 20l-4-4 M11 8v6M8 11h6"),
            Map.entry("zoom-out", "M4 11a7 7 0 1 0 14 0 7 7 0 1 0-14 0 M20 20l-4-4 M8 11h6"),
            Map.entry("chevron-left", "M15 6l-6 6 6 6"),
            Map.entry("chevron-right", "M9 6l6 6-6 6"),
            Map.entry("more", "M5 12h.01M12 12h.01M19 12h.01"),
            Map.entry("empty", "M4 13h4l2 3h4l2-3h4M4 13l2-8h12l2 8v6H4z")
    );

    public Map<String, String> getPaths() {
        return PATHS;
    }

    public String path(String name) {
        String p = PATHS.get(name);
        return p == null ? PATHS.get("info") : p;
    }
}
