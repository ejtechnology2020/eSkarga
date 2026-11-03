package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

import pl.ejtechnology.tk.eSkarga.domain.CaseFlag;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;

/** Kafelek sprawy / wiersz repertorium (arkusz komponentów: „Kafelek sprawy”). */
public record CaseSummary(DocketNumber sygnatura, String typ, CaseStatus status, String skarzacy, String pelnomocnik,
                          String sedzia, Set<CaseFlag> flagi, DeadlineView termin, DocketNumber powiazana,
                          String przedmiot, LocalDate dataWpisu, String sposobZakonczenia, LocalDate dataZakonczenia,
                          String wersja) implements Serializable {

    public boolean hasFlag(CaseFlag f) {
        return flagi != null && flagi.contains(f);
    }

    public boolean isReadOnly() {
        return status.isReadOnly();
    }

    public String sygnaturaText() {
        return sygnatura.format();
    }
}
