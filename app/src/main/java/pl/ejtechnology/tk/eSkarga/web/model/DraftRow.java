package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Projekt pisma w aktach (czynność w stanie PROJEKT) do podpisu na S-16: dokument, rodzaj (kod LetterKind), tytuł, autor, data. */
public record DraftRow(long dokumentId, String rodzaj, String tytul, String autor, LocalDateTime data) implements Serializable {
}
