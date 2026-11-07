package pl.ejtechnology.tk.eSkarga.domain;

/**
 * Warunek, przy którym wymóg formalny trafia na listę kontrolną sprawy (ADR 0015, BR-05): zawsze, tylko gdy pisma nie sporządził
 * adwokat lub radca prawny, albo tylko gdy brak zastępstwa i skarżący nie jest zwolniony z przymusu adwokackiego (art. 44 ust. 1).
 */
public enum RequirementCondition {
    ZAWSZE("zawsze"),
    BEZ_PELNOMOCNIKA("gdy pisma nie sporządził adwokat ani radca prawny"),
    BRAK_ZASTEPSTWA("gdy brak zastępstwa, a skarżący nie jest zwolniony (art. 44 ust. 1)");

    private final String label;

    RequirementCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean applies(boolean professionalAttorney, boolean exempt) {
        return switch (this) {
            case ZAWSZE -> true;
            case BEZ_PELNOMOCNIKA -> !professionalAttorney;
            case BRAK_ZASTEPSTWA -> !professionalAttorney && !exempt;
        };
    }
}
