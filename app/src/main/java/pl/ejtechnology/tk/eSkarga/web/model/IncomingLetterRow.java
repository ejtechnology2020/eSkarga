package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Wiersz dziennika wpływu (S-04). */
public record IncomingLetterRow(String nr, LocalDateTime wplyw, LocalDate wniesienie, IntakeChannel kanal, String nadawca,
                                String kwalifikacja, DocketNumber sprawa, String podpowiedz, String stan, Tone stanTone,
                                Tone wiersz, boolean wybrany, int strony, int zalaczniki) implements Serializable {
}
