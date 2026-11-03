package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Postanowienie czekające na publikację (S-27, UC-16). */
public record PublicationItem(DocketNumber sprawa, String tag, Tone tagTone, boolean flagaArt80, String tytul, String uwagi,
                              int zamiany, boolean wybrany) implements Serializable {
}
