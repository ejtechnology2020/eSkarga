package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Zdarzenie z dziennika zdarzeń (S-25, NFR-02). */
public record AuditRow(long nr, LocalDateTime czas, String uzytkownik, String rola, String czynnosc, String sprawa,
                       String obiekt, String adres, String skrot, String skrotPoprzedni, String podstawa, String login,
                       boolean wybrany) implements Serializable {
}
