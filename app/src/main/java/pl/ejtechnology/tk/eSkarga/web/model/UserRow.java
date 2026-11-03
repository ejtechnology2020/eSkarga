package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Użytkownik z ID-users (tylko odczyt) z przydziałem w aplikacji (S-22). */
public record UserRow(String nazwa, String komorka, String role, LocalDateTime logowanie, String przydzial,
                      boolean przydzialEdytowalny, String upowaznienie, boolean wybrany) implements Serializable {
}
