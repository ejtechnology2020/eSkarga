package pl.ejtechnology.tk.eSkarga.process;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Zmienne procesu „Wstępna kontrola” – wyłącznie identyfikatory i daty (D8, CLAUDE.md zasada 5).
 * Kluczem biznesowym procesu jest sygnatura. Ta klasa jest jedynym miejscem, które tworzy mapy zmiennych,
 * a test sprawdza, że żadna nazwa nie wskazuje na dane osobowe.
 */
public final class ProcessVariables {

    public static final String CASE_ID = "caseId";
    public static final String SYGNATURA = "sygnatura";
    public static final String SEDZIA_ID = "sedziaId";
    public static final String RODZAJ_PISMA = "rodzajPisma";
    public static final String WYNIK_KONTROLI = "wynikKontroli";
    public static final String TERMIN_BRAKOW = "terminBrakow";
    public static final String TERMIN_ZAZALENIA = "terminZazalenia";
    public static final String ZAZALENIE_W_TERMINIE = "zazalenieWTerminie";
    public static final String ZAZALENIE_ZASADNE = "zazalenieZasadne";

    /** Dozwolone nazwy zmiennych. Każda inna jest odrzucana przez {@link #validate(Map)}. */
    public static final Set<String> ALLOWED = Set.of(CASE_ID, SYGNATURA, SEDZIA_ID, RODZAJ_PISMA, WYNIK_KONTROLI,
            TERMIN_BRAKOW, TERMIN_ZAZALENIA, ZAZALENIE_W_TERMINIE, ZAZALENIE_ZASADNE);

    /** Rodzaj pisma po kwalifikacji (bramka „Skarga lub wniosek?”). */
    public enum RodzajPisma { SKARGA, WNIOSEK, OGOLNE }

    /** Wynik wstępnej kontroli (bramka „Wynik kontroli?”). */
    public enum WynikKontroli { BRAKI, NADANIE_BIEGU, ODMOWA }

    private ProcessVariables() {
    }

    public static Map<String, Object> start(long caseId, String sygnatura, RodzajPisma rodzaj) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put(CASE_ID, caseId);
        v.put(SYGNATURA, sygnatura);
        v.put(RODZAJ_PISMA, rodzaj.name());
        return validate(v);
    }

    public static Map<String, Object> judgeAssigned(String judgeUserId) {
        return validate(Map.of(SEDZIA_ID, judgeUserId));
    }

    public static Map<String, Object> reviewResult(WynikKontroli wynik) {
        return validate(Map.of(WYNIK_KONTROLI, wynik.name()));
    }

    /** Data końca terminu wyliczona przez domenę (DeadlineCalculator), nie przez timer P7D. */
    public static Map<String, Object> deadline(String variable, Date end) {
        return validate(Map.of(variable, end));
    }

    public static Map<String, Object> appealTimeliness(boolean inTime) {
        return validate(Map.of(ZAZALENIE_W_TERMINIE, inTime));
    }

    public static Map<String, Object> appealOutcome(boolean upheld) {
        return validate(Map.of(ZAZALENIE_ZASADNE, upheld));
    }

    /** Odrzuca nazwy poza listą oraz wartości innych typów niż liczby, teksty identyfikatorów, daty i flagi. */
    public static Map<String, Object> validate(Map<String, Object> variables) {
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            if (!ALLOWED.contains(e.getKey())) {
                throw new IllegalArgumentException("Zmienna procesu niedozwolona (D8): " + e.getKey());
            }
            Object val = e.getValue();
            if (!(val == null || val instanceof Number || val instanceof Boolean || val instanceof Date || val instanceof String)) {
                throw new IllegalArgumentException("Typ zmiennej procesu niedozwolony (D8): " + e.getKey() + " = " + val.getClass());
            }
        }
        return variables;
    }
}
