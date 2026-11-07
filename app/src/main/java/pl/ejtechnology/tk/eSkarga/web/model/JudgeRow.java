package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Wpis słownika sędziów (S-24, ADR 0027): login z ID-users, nazwa, funkcja, kadencja, stan, czy dziś można wyznaczyć. */
public record JudgeRow(Long id, String login, String nazwa, String funkcja, String funkcjaOpis, LocalDate od, LocalDate doDnia, boolean aktywny,
                       boolean wKadencji, String zmieniono) implements Serializable {

    public String rowClass() {
        return !aktywny ? "off" : wKadencji ? "" : "warn";
    }

    public String stan() {
        return !aktywny ? "wycofany" : wKadencji ? "w kadencji" : "poza kadencją";
    }
}
