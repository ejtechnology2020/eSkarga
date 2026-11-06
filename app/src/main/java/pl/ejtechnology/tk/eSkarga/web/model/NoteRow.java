package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Notatka wewnętrzna zespołu sędziego w aktach (FR-24, S-14): tytuł, treść, autor (nazwa z rejestru S-22 albo login), data. */
public record NoteRow(Long id, String tytul, String tresc, String autor, LocalDateTime data) implements Serializable {
}
