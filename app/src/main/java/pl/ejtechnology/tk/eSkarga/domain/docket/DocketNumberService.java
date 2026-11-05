package pl.ejtechnology.tk.eSkarga.domain.docket;

import java.time.LocalDate;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;

/**
 * Usługa sygnatur (T-012). {@code MANDATORY}: musi działać w transakcji wywołującego, czyli razem
 * z wpisem do repertorium i startem procesu (D1).
 */
@Stateless
@LocalBean   // wstrzykiwany także przez klasę (CaseFileService, ZalozenieBean), nie tylko przez DocketCounterRepository
public class DocketNumberService implements DocketCounterRepository {

    @PersistenceContext(unitName = "repertorium")
    private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public DocketNumber nextNumber(DocketSymbol symbol, LocalDate entryDate) {
        return new DocketNumberAllocator(this).next(symbol, entryDate);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public DocketNumber peekNextNumber(DocketSymbol symbol, LocalDate entryDate) {
        return new DocketNumberAllocator(this).peek(symbol, entryDate);
    }

    @Override
    public int lockAndGetLastValue(DocketSymbol symbol, int year) {
        DocketCounter counter = em.find(DocketCounter.class, new DocketCounter.Key(symbol.code(), year),
                LockModeType.PESSIMISTIC_WRITE);
        if (counter == null) {
            // pierwszy numer w roku: wstawienie odporne na wyścig równoległych transakcji (CounterRows), potem blokada wiersza
            CounterRows.ensureDocketCounter(em, symbol.code(), year);
            counter = em.find(DocketCounter.class, new DocketCounter.Key(symbol.code(), year), LockModeType.PESSIMISTIC_WRITE);
        }
        return counter.getLastValue();
    }

    @Override
    public void updateLastValue(DocketSymbol symbol, int year, int newValue) {
        DocketCounter counter = em.find(DocketCounter.class, new DocketCounter.Key(symbol.code(), year));
        counter.setLastValue(newValue);
    }
}
