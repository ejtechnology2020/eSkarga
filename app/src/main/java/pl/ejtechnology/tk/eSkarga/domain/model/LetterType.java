package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Słownik typów pism. */
@Entity
@Table(name = "letter_type")
public class LetterType extends DictionaryEntry {
    protected LetterType() {
    }

    public LetterType(String code, String name) {
        super(code, name);
    }
}
