package pl.ejtechnology.tk.eSkarga.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Słownik organów. */
@Entity
@Table(name = "authority")
public class Authority extends DictionaryEntry {
    protected Authority() {
    }

    public Authority(String code, String name) {
        super(code, name);
    }
}
