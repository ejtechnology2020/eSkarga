package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

/** Krok paska przebiegu sprawy (wzorzec „Wariant C · Przebieg”). */
public record ProcessStep(String label, LocalDate date, String dateNote, boolean done, boolean current, String next, int number)
        implements Serializable {
}
