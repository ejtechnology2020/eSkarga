package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;

/** Słupek poziomy z wartością w tekście (obciążenie, sposoby zakończenia). */
public record HBar(String label, int value, int percent, boolean warn) implements Serializable {
}
