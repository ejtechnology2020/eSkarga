package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Konto z rejestru osób znanych eSkardze (S-22) do podpowiedzi loginu w słownikach: login i nazwa z ID-users. */
public record AccountRow(String login, String nazwa) implements Serializable {

    public String etykieta() {
        return nazwa == null || nazwa.isBlank() || nazwa.equals(login) ? login : nazwa + " (" + login + ")";
    }
}
