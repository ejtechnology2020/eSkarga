package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Skan pisma z rejestracji wpływu do podglądu w kolejce i dzienniku (S-04, S-06): adres serwletu {@code /wplyw/skan/<id>}. */
public record ScanRow(long id, String nazwa, String contentType, long rozmiar) implements Serializable {

    public boolean pdf() {
        return "application/pdf".equals(contentType);
    }

    public String rozmiarTekst() {
        return rozmiar >= 1_048_576 ? String.format("%.1f MB", rozmiar / 1_048_576.0) : String.format("%d kB", Math.max(1, rozmiar / 1024));
    }
}
