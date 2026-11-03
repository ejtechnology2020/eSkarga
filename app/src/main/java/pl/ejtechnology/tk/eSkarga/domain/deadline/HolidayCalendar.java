package pl.ejtechnology.tk.eSkarga.domain.deadline;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Kalendarz dni wolnych używany przy liczeniu terminów (BR-02, D6). */
public interface HolidayCalendar {

    /** Czy dzień jest ustawowo wolny od pracy albo wolny w Trybunale. */
    boolean isHoliday(LocalDate date);

    /** Dzień roboczy: nie sobota, nie niedziela, nie dzień wolny. */
    default boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !isHoliday(date);
    }

    /** Najbliższy dzień roboczy od podanej daty włącznie. */
    default LocalDate nextWorkingDayOnOrAfter(LocalDate date) {
        LocalDate d = date;
        int guard = 0;
        while (!isWorkingDay(d)) {
            d = d.plusDays(1);
            if (++guard > 60) {
                throw new IllegalStateException("Kalendarz bez dni roboczych od " + date);
            }
        }
        return d;
    }
}
