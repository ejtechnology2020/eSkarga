package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Słownik sądów. */
@Entity
@Table(name = "court")
public class Court extends DictionaryEntry {
    protected Court() {
    }

    public Court(String code, String name) {
        super(code, name);
    }
}
