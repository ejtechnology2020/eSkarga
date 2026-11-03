package pl.ejtechnology.tk.eSkarga.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.ejtechnology.tk.eSkarga.domain.deadline.PolishHolidays;
import pl.ejtechnology.tk.eSkarga.web.model.CalendarMonth;

/** Kalendarz dni wolnych do S-24 (święta ustawowe + dni wolne TK z tabeli {@code holiday}). */
public final class CalendarBuilder {

    private static final String[] NAMES = {"Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec", "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"};

    private CalendarBuilder() {
    }

    public static Map<LocalDate, String> statutoryNames(int year) {
        Map<LocalDate, String> hol = new LinkedHashMap<>();
        hol.put(LocalDate.of(year, 1, 1), "Nowy Rok");
        hol.put(LocalDate.of(year, 1, 6), "Trzech Króli");
        LocalDate easter = PolishHolidays.easterSunday(year);
        hol.put(easter, "Wielkanoc");
        hol.put(easter.plusDays(1), "Poniedziałek Wielkanocny");
        hol.put(LocalDate.of(year, 5, 1), "Święto Pracy");
        hol.put(LocalDate.of(year, 5, 3), "Święto Konstytucji 3 Maja");
        hol.put(easter.plusDays(49), "Zielone Świątki");
        hol.put(easter.plusDays(60), "Boże Ciało");
        hol.put(LocalDate.of(year, 8, 15), "Wniebowzięcie NMP");
        hol.put(LocalDate.of(year, 11, 1), "Wszystkich Świętych");
        hol.put(LocalDate.of(year, 11, 11), "Narodowe Święto Niepodległości");
        hol.put(LocalDate.of(year, 12, 24), "Wigilia");
        hol.put(LocalDate.of(year, 12, 25), "Boże Narodzenie");
        hol.put(LocalDate.of(year, 12, 26), "Drugi dzień Bożego Narodzenia");
        return hol;
    }

    public static List<CalendarMonth> build(int year, Map<LocalDate, String> ownDaysOff) {
        Map<LocalDate, String> hol = statutoryNames(year);
        List<CalendarMonth> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate first = LocalDate.of(year, m, 1);
            int lead = first.getDayOfWeek().getValue() - 1;
            List<CalendarMonth.Cell> cells = new ArrayList<>();
            for (int i = 0; i < lead; i++) {
                cells.add(new CalendarMonth.Cell("", "", ""));
            }
            for (int day = 1; day <= first.lengthOfMonth(); day++) {
                LocalDate dd = LocalDate.of(year, m, day);
                String cls = hol.containsKey(dd) ? "hol" : ownDaysOff.containsKey(dd) ? "own" : dd.getDayOfWeek().getValue() >= 6 ? "we" : "";
                String title = hol.getOrDefault(dd, ownDaysOff.getOrDefault(dd, ""));
                cells.add(new CalendarMonth.Cell(String.valueOf(day), cls, title));
            }
            months.add(new CalendarMonth(NAMES[m - 1], cells));
        }
        return months;
    }

    public static Set<LocalDate> ofYear(Set<LocalDate> days, int year) {
        Set<LocalDate> out = new java.util.TreeSet<>();
        for (LocalDate d : days) {
            if (d.getYear() == year) {
                out.add(d);
            }
        }
        return out;
    }
}
