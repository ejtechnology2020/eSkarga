package pl.ejtechnology.tk.eSkarga.domain.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolishHolidaysTest {

    @ParameterizedTest
    @CsvSource({"2024,2024-03-31", "2025,2025-04-20", "2026,2026-04-05", "2027,2027-03-28", "2028,2028-04-16", "2030,2030-04-21"})
    void easterSunday(int year, LocalDate expected) {
        assertThat(PolishHolidays.easterSunday(year)).isEqualTo(expected);
    }

    @Test
    void statutoryHolidays2026() {
        Set<LocalDate> s = PolishHolidays.statutory(2026);
        assertThat(s).contains(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 24), LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 12, 24),
                LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26));
        assertThat(s).hasSize(14);
    }

    @Test
    void christmasEveOnlyFrom2025() {
        assertThat(PolishHolidays.statutory(2024)).doesNotContain(LocalDate.of(2024, 12, 24));
        assertThat(PolishHolidays.statutory(2025)).contains(LocalDate.of(2025, 12, 24));
    }

    @Test
    void workingDaysSkipWeekendsHolidaysAndExtraDaysOff() {
        PolishHolidays cal = new PolishHolidays(Set.of(LocalDate.of(2026, 11, 10)));
        assertThat(cal.isWorkingDay(LocalDate.of(2026, 11, 3))).isTrue();
        assertThat(cal.isWorkingDay(LocalDate.of(2026, 11, 7))).isFalse();
        assertThat(cal.isWorkingDay(LocalDate.of(2026, 11, 10))).isFalse();
        assertThat(cal.isWorkingDay(LocalDate.of(2026, 11, 11))).isFalse();
        assertThat(cal.nextWorkingDayOnOrAfter(LocalDate.of(2026, 11, 7))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(cal.nextWorkingDayOnOrAfter(LocalDate.of(2026, 11, 10))).isEqualTo(LocalDate.of(2026, 11, 12));
    }
}
