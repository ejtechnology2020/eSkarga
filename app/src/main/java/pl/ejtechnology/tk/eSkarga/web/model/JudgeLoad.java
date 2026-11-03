package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Obciążenie sędziego (S-17, S-19, S-01 Prezes). */
public record JudgeLoad(String sedzia, String nazwisko, int sprawy, int percent, int terminyTydzien, String terminyUwaga,
                        String nieobecnosc, Tone nieobecnoscTone, boolean wybrany, boolean dostepny, String powodNiedostepnosci)
        implements Serializable {
}
