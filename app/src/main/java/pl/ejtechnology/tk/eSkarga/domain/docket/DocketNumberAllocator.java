package pl.ejtechnology.tk.eSkarga.domain.docket;

import java.time.LocalDate;
import java.util.Objects;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;

/**
 * Nadaje kolejną sygnaturę bez luk (BR-01, FR-06). Działa w transakcji wywołującego (D1): jeśli wpis
 * do repertorium albo start procesu Operaton się nie uda, wycofanie transakcji zwalnia numer, bo licznik
 * jest zwykłym wierszem, a nie sekwencją. Numeracja zaczyna się od 1 w każdym roku.
 */
public final class DocketNumberAllocator {

    private final DocketCounterRepository counters;

    public DocketNumberAllocator(DocketCounterRepository counters) {
        this.counters = Objects.requireNonNull(counters);
    }

    public DocketNumber next(DocketSymbol symbol, LocalDate entryDate) {
        if (!symbol.isManagedHere()) {
            throw new IllegalArgumentException("Repertorium " + symbol.code() + " nie jest prowadzone w tym systemie");
        }
        int year = entryDate.getYear();
        int last = counters.lockAndGetLastValue(symbol, year);
        int next = last + 1;
        counters.updateLastValue(symbol, year, next);
        return new DocketNumber(symbol, next, year);
    }

    /** Podgląd kolejnej sygnatury do okna potwierdzenia (S-07) – bez rezerwacji numeru. */
    public DocketNumber peek(DocketSymbol symbol, LocalDate entryDate) {
        int year = entryDate.getYear();
        int last = counters.lockAndGetLastValue(symbol, year);
        return new DocketNumber(symbol, last + 1, year);
    }
}
