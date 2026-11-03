package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Skutek czynności w oknie potwierdzenia (ostrzeżenie = krok nieodwracalny). */
public record Effect(String icon, String text, boolean warn) implements Serializable {

    public static Effect next(String text) {
        return new Effect("arrow-right", text, false);
    }

    public static Effect timer(String text) {
        return new Effect("timer", text, false);
    }

    public static Effect warning(String text) {
        return new Effect("alert-triangle", text, true);
    }
}
