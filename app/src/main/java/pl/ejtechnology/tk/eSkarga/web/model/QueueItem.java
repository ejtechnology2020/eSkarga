package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Pismo w kolejce do kwalifikacji (S-06). */
public record QueueItem(String nr, String nadawca, IntakeChannel kanal, String etykieta, Tone etykietaTone,
                        String podpowiedz, boolean mocnaPodpowiedz, boolean wybrane, Tone wiersz) implements Serializable {

    public String cssClass() {
        if (wybrane) {
            return "case on";
        }
        return wiersz == Tone.ERR ? "case err" : "case";
    }
}
