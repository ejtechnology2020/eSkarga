package pl.ejtechnology.tk.eSkarga.domain.deadline;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dni ustawowo wolne od pracy w Polsce (ustawa z 18.01.1951 r. o dniach wolnych od pracy,
 * z Wigilią od 2025 r.) oraz konfigurowalne dni wolne w Trybunale (np. dzień wolny za święto w sobotę).
 * Reguły pozostają do potwierdzenia przez Biuro Służby Prawnej (T-025 [decyzja]).
 */
public final class PolishHolidays implements HolidayCalendar {

    private final Set<LocalDate> extraDaysOff;
    private final Map<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();

    public PolishHolidays() {
        this(Collections.emptySet());
    }

    public PolishHolidays(Set<LocalDate> extraDaysOff) {
        this.extraDaysOff = Set.copyOf(extraDaysOff);
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        return extraDaysOff.contains(date) || cache.computeIfAbsent(date.getYear(), PolishHolidays::statutory).contains(date);
    }

    public Set<LocalDate> extraDaysOff() {
        return extraDaysOff;
    }

    /** Święta ustawowe w danym roku, w tym ruchome liczone od Wielkanocy. */
    public static Set<LocalDate> statutory(int year) {
        Set<LocalDate> s = new HashSet<>();
        s.add(LocalDate.of(year, Month.JANUARY, 1));
        s.add(LocalDate.of(year, Month.JANUARY, 6));
        s.add(LocalDate.of(year, Month.MAY, 1));
        s.add(LocalDate.of(year, Month.MAY, 3));
        s.add(LocalDate.of(year, Month.AUGUST, 15));
        s.add(LocalDate.of(year, Month.NOVEMBER, 1));
        s.add(LocalDate.of(year, Month.NOVEMBER, 11));
        if (year >= 2025) {
            s.add(LocalDate.of(year, Month.DECEMBER, 24));
        }
        s.add(LocalDate.of(year, Month.DECEMBER, 25));
        s.add(LocalDate.of(year, Month.DECEMBER, 26));
        LocalDate easter = easterSunday(year);
        s.add(easter);
        s.add(easter.plusDays(1));
        s.add(easter.plusDays(49)); // Zielone Świątki (niedziela)
        s.add(easter.plusDays(60)); // Boże Ciało
        return Collections.unmodifiableSet(s);
    }

    /** Wielkanoc według algorytmu Meeusa/Jonesa/Butchera (kalendarz gregoriański). */
    public static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
