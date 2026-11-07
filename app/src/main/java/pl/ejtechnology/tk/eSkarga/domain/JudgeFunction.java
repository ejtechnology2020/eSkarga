package pl.ejtechnology.tk.eSkarga.domain;

/** Funkcja sędziego w słowniku sędziów (S-24): Prezes TK, Wiceprezes TK albo sędzia. */
public enum JudgeFunction {
    PREZES("Prezes TK"),
    WICEPREZES("Wiceprezes TK"),
    SEDZIA("sędzia TK");

    private final String label;

    JudgeFunction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
