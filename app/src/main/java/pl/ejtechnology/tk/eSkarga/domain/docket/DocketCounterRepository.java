package pl.ejtechnology.tk.eSkarga.domain.docket;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;

/**
 * Dostęp do licznika {@code docket_counter(symbol, year, last_value)}. Implementacja JPA blokuje
 * wiersz zapytaniem {@code SELECT … FOR UPDATE} w transakcji wywołującego; sekwencje bazy są zabronione (BR-01).
 */
public interface DocketCounterRepository {

    /**
     * Zwraca bieżącą wartość licznika, zakładając blokadę wiersza do końca transakcji.
     * Gdy wiersz nie istnieje, tworzy go z wartością 0.
     */
    int lockAndGetLastValue(DocketSymbol symbol, int year);

    /** Zapisuje nową wartość licznika w zablokowanym wierszu. */
    void updateLastValue(DocketSymbol symbol, int year, int newValue);
}
