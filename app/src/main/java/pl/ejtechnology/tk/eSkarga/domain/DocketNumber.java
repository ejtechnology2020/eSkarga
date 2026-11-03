package pl.ejtechnology.tk.eSkarga.domain;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sygnatura sprawy (BR-01): symbol + kolejny numer w roku + „/” + dwie ostatnie cyfry roku wpisu,
 * np. {@code Ts 312/26}. Numeracja zaczyna się od 1 w każdym roku i nie ma luk.
 */
public record DocketNumber(DocketSymbol symbol, int number, int year) implements Serializable {

    private static final Pattern FORMAT = Pattern.compile("^\\s*([A-Za-z]{1,3})\\s+(\\d{1,6})/(\\d{2}|\\d{4})\\s*$");

    public DocketNumber {
        if (number < 1) {
            throw new IllegalArgumentException("Numer sprawy musi być dodatni: " + number);
        }
        if (year < 1997 || year > 2199) {
            throw new IllegalArgumentException("Rok wpisu poza zakresem: " + year);
        }
    }

    /** Postać urzędowa, np. „Ts 312/26”. */
    public String format() {
        return symbol.code() + " " + number + "/" + String.format("%02d", year % 100);
    }

    public static DocketNumber parse(String text) {
        Matcher m = FORMAT.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Niepoprawna sygnatura: " + text);
        }
        int y = Integer.parseInt(m.group(3));
        if (y < 100) {
            y += y >= 97 ? 1900 : 2000;
        }
        return new DocketNumber(DocketSymbol.fromCode(m.group(1)), Integer.parseInt(m.group(2)), y);
    }

    @Override
    public String toString() {
        return format();
    }
}
