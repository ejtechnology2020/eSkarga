package pl.ejtechnology.tk.eSkarga.domain.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BR-02 – BR-04 (T-025): ≥ 30 przypadków parametrycznych – święta ruchome, koniec miesiąca, rok przestępny, wstrzymanie.
 * Reguły pozostają do potwierdzenia przez Biuro Służby Prawnej; testy dokumentują przyjętą interpretację.
 */
class DeadlineCalculatorTest {

    private final DeadlineCalculator calc = new DeadlineCalculator(new PolishHolidays(Set.of(LocalDate.of(2026, 11, 10))));

    @ParameterizedTest(name = "doręczenie {0} → koniec 7 dni {1}")
    @CsvSource({
            // zwykłe dni robocze
            "2026-11-02,2026-11-09",   // Ts 312/26: doręczono 02.11, koniec 09.11 (poniedziałek)
            "2026-10-28,2026-11-04",   // Ts 301/26: zażalenie do 04.11
            "2026-10-05,2026-10-12",
            // koniec w sobotę/niedzielę → najbliższy dzień roboczy
            "2026-10-31,2026-11-09",   // 07.11 sobota → 09.11 poniedziałek
            "2026-11-01,2026-11-09",   // 08.11 niedziela → 09.11
            // koniec w święto ustawowe
            "2026-11-04,2026-11-12",   // 11.11 Narodowe Święto Niepodległości (środa) → 12.11
            "2026-11-03,2026-11-12",   // 10.11 dzień wolny w Trybunale (za 15.08) → 11.11 święto → 12.11
            "2026-12-17,2026-12-28",   // 24.12 Wigilia (czwartek, wolna od 2025) → 25, 26 święta → 27 niedziela → 28.12
            "2026-12-25,2027-01-04",   // 01.01 Nowy Rok (piątek) → 02.01 sobota → 03.01 niedziela → 04.01
            "2025-12-30,2026-01-07",   // 06.01 Trzech Króli (wtorek) → 07.01
            // Wielkanoc 2026: 05.04 (niedziela), Poniedziałek Wielkanocny 06.04
            "2026-03-29,2026-04-07",   // 05.04 Wielkanoc → 06.04 święto → 07.04
            "2026-03-30,2026-04-07",   // 06.04 → 07.04
            // Boże Ciało 2026: 04.06; Zielone Świątki 24.05
            "2026-05-28,2026-06-05",   // 04.06 Boże Ciało (czwartek) → 05.06
            "2026-05-17,2026-05-25",   // 24.05 niedziela (Zielone Świątki) → 25.05
            // 1 i 3 maja
            "2026-04-24,2026-05-04",   // 01.05 piątek → 02.05 sobota → 03.05 niedziela/święto → 04.05
            // sierpień 15.08.2026 sobota → nie przesuwa, bo koniec 15.08 to sobota → 17.08
            "2026-08-08,2026-08-17",
            // rok przestępny 2028: 29.02 istnieje
            "2028-02-22,2028-02-29",
            "2028-02-23,2028-03-01",
            // koniec miesiąca
            "2026-01-24,2026-02-02",   // 31.01 sobota → 02.02
            "2026-06-23,2026-06-30",
    })
    void sevenDayDeadlineEnds(LocalDate delivery, LocalDate expectedEnd) {
        DeadlineComputation c = calc.sevenDaysFromDelivery(delivery);
        assertThat(c.start()).isEqualTo(delivery.plusDays(1));
        assertThat(c.end()).isEqualTo(expectedEnd);
    }

    @Test
    void sevenDaysEndingOnWorkingDayIsNotMoved() {
        // 29.10.2026 + 7 = 05.11.2026 (czwartek) – dzień roboczy, bez przesunięcia
        DeadlineComputation c = calc.sevenDaysFromDelivery(LocalDate.of(2026, 10, 29));
        assertThat(c.nominalEnd()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(c.end()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(c.movedToWorkingDay()).isFalse();
    }

    @ParameterizedTest(name = "doręczenie orzeczenia {0} → koniec 3 miesięcy {1}")
    @CsvSource({
            "2026-07-14,2026-10-14",   // Ts 312/26: doręczono 14.07 → 14.10 (środa)
            "2026-07-20,2026-10-20",   // Ts 315/26 (pomocniczo)
            "2026-08-05,2026-11-05",   // Ts 319/26
            "2026-07-31,2026-11-02",   // 31.10 sobota → 02.11
            "2026-08-31,2026-11-30",   // 30.11 (listopad ma 30 dni) → poniedziałek
            "2025-11-30,2026-03-02",   // 28.02.2026 sobota → 02.03
            "2027-11-29,2028-02-29",   // rok przestępny: 29.02.2028 (wtorek)
            "2026-09-01,2026-12-01",
            "2026-08-11,2026-11-12",   // 11.11 święto → 12.11
            "2026-02-05,2026-05-05",   // 05.05 wtorek
    })
    void threeMonthDeadlineEnds(LocalDate rulingDelivery, LocalDate expectedEnd) {
        DeadlineComputation c = calc.threeMonthsFromRulingDelivery(rulingDelivery, List.of());
        assertThat(c.end()).isEqualTo(expectedEnd);
    }

    @Test
    void threeMonthsFromEndOfJanuaryEndsOnLastDayOfApril() {
        DeadlineComputation c = calc.threeMonthsFromRulingDelivery(LocalDate.of(2026, 1, 31), List.of());
        assertThat(c.nominalEnd()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void suspensionForCourtAppointedAttorneyExtendsDeadline() {
        // BR-03: wniosek o pełnomocnika z urzędu 01.08–20.08 (20 dni) wstrzymuje bieg
        DeadlineComputation c = calc.threeMonthsFromRulingDelivery(LocalDate.of(2026, 7, 14),
                List.of(new Suspension(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20))));
        assertThat(c.suspendedDays()).isEqualTo(20);
        assertThat(c.nominalEnd()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(c.end()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(c.suspended()).isFalse();
    }

    @Test
    void openSuspensionIsReported() {
        DeadlineComputation c = calc.threeMonthsFromRulingDelivery(LocalDate.of(2026, 7, 14),
                List.of(new Suspension(LocalDate.of(2026, 10, 1), null)));
        assertThat(c.suspended()).isTrue();
        assertThat(c.suspendedDays()).isZero();
    }

    @Test
    void suspensionBeforeStartDoesNotCount() {
        DeadlineComputation c = calc.threeMonthsFromRulingDelivery(LocalDate.of(2026, 7, 14),
                List.of(new Suspension(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10))));
        assertThat(c.suspendedDays()).isZero();
    }

    @ParameterizedTest(name = "wniesiono {0}, koniec {1} → w terminie {2}")
    @CsvSource({"2026-10-13,2026-10-14,true", "2026-10-14,2026-10-14,true", "2026-10-15,2026-10-14,false", "2026-10-29,2026-10-20,false"})
    void filingDateDecidesTimeliness(LocalDate filed, LocalDate end, boolean inTime) {
        assertThat(calc.filedInTime(filed, end)).isEqualTo(inTime); // BR-04: data nadania/UPO, nie wpływu
    }

    @Test
    void daysLeftAndProgress() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        DeadlineComputation c = calc.sevenDaysFromDelivery(LocalDate.of(2026, 11, 2));
        assertThat(calc.daysLeft(c.end(), today)).isEqualTo(6);
        assertThat(calc.progressPercent(c.start(), c.end(), today)).isEqualTo(14);
        assertThat(calc.progressPercent(c.start(), c.end(), c.end())).isEqualTo(100);
        assertThat(calc.progressPercent(c.start(), c.end(), c.end().plusDays(3))).isEqualTo(100);
    }
}
