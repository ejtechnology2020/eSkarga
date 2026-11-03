package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

import pl.ejtechnology.tk.eSkarga.domain.Tone;

/** Kafelek liczbowy; klik filtruje tabelę. Kolor tylko, gdy znaczy; zawsze z tekstem. */
public record KpiTile(String value, String label, String hint, Tone tone, boolean selected, String href) implements Serializable {

    public String cssClass() {
        StringBuilder sb = new StringBuilder("kpi");
        if (tone != null && !tone.css().isEmpty()) {
            sb.append(' ').append(tone.css());
        }
        if (selected) {
            sb.append(" sel");
        }
        return sb.toString();
    }
}
