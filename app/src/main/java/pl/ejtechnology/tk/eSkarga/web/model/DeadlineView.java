package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.DeadlineState;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;

/**
 * Wskaźnik terminu do widoku (arkusz komponentów). Tekst zawsze zawiera datę i liczbę dni –
 * kolor nigdy nie występuje sam (WCAG 2.1 AA).
 *
 * @param type     rodzaj terminu (etykieta z pakietu komunikatów)
 * @param state    stan wskaźnika (normalny, ostrzegawczy, po terminie, wstrzymany, pomocniczy, brak)
 * @param end      koniec terminu, może być {@code null} przy samym oczekiwaniu
 * @param progress postęp paska 0–100, {@code null} = bez paska
 * @param text     opis po etykiecie, np. „do 09.11.2026 · za 6 dni”
 * @param prefix   czy pokazać pogrubioną etykietę rodzaju terminu
 */
public record DeadlineView(DeadlineType type, DeadlineState state, LocalDate end, Integer progress, String text, boolean prefix)
        implements Serializable {

    public static DeadlineView none(String text) {
        return new DeadlineView(DeadlineType.OCZEKIWANIE, DeadlineState.NONE, null, null, text, false);
    }

    public boolean hasBar() {
        return progress != null;
    }

    public String icon() {
        return switch (state) {
            case WARNING -> "alert-triangle";
            case OVERDUE -> "alert-circle";
            case SUSPENDED -> "pause";
            case AUXILIARY -> "info";
            case NONE -> "clock";
            default -> "timer";
        };
    }
}
