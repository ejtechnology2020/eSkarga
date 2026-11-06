package pl.ejtechnology.tk.eSkarga.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BR-01: sygnatura = symbol + kolejny nr w roku + „/” + dwie ostatnie cyfry roku. */
class DocketNumberTest {

    @ParameterizedTest
    @CsvSource({"TS,312,2026,Ts 312/26", "TW,14,2026,Tw 14/26", "TS,1,2027,Ts 1/27", "SK,41,2026,SK 41/26", "TS,5,2005,Ts 5/05"})
    void formatsOfficialForm(String symbol, int number, int year, String expected) {
        assertThat(new DocketNumber(DocketSymbol.valueOf(symbol), number, year).format()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Ts 312/26,TS,312,2026", "tw 14/26,TW,14,2026", "  Ts 7/2026 ,TS,7,2026", "SK 41/26,SK,41,2026", "Ts 3/99,TS,3,1999"})
    void parsesOfficialForm(String text, String symbol, int number, int year) {
        DocketNumber n = DocketNumber.parse(text);
        assertThat(n.symbol()).isEqualTo(DocketSymbol.valueOf(symbol));
        assertThat(n.number()).isEqualTo(number);
        assertThat(n.year()).isEqualTo(year);
    }

    @Test
    void rejectsZeroAndNegativeNumbers() {
        assertThatThrownBy(() -> new DocketNumber(DocketSymbol.TS, 0, 2026)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocketNumber.parse("Ts 0/26")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> DocketNumber.parse("Testowa")).isInstanceOf(IllegalArgumentException.class);
        // brak sygnatury (np. ekran bez wybranej sprawy) – czytelny wyjątek zamiast NullPointerException z Matchera
        assertThatThrownBy(() -> DocketNumber.parse(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Brak sygnatury");
        assertThatThrownBy(() -> DocketNumber.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocketNumber.parse("Xy 1/26")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTrips() {
        assertThat(DocketNumber.parse("Ts 312/26").format()).isEqualTo("Ts 312/26");
    }
}
