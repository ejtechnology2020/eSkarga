package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Wniosek o wgląd w akta (S-13, FR-27). */
public record AccessRequestRow(String wnioskodawca, String kto, Tone ktoTone, DocketNumber sprawa, String zakres,
                               String termin, boolean terminWyznaczony, String oplata, String anonimizacja, Tone anonTone,
                               boolean osobaTrzecia, boolean wybrany) implements Serializable {
}
