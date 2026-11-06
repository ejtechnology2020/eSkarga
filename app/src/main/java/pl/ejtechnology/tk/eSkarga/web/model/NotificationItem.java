package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Powiadomienie dla zalogowanej osoby (dzwonek na górnym pasku): liczone z danych, które już są – nowe zadania od poprzedniego
 * logowania, terminy ≤ 2 dni, nowe notatki zespołu sędziego, sprawy do wyznaczenia (Prezes). {@code klucz} identyfikuje pozycję
 * (rodzaj + id) – po otwarciu panelu zapamiętany w sesji jako obejrzany, bez tabeli powiadomień w bazie.
 */
public record NotificationItem(String klucz, String rodzaj, String tytul, String opis, String url, LocalDateTime kiedy) implements Serializable {
}
