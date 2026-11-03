package pl.ejtechnology.tk.eSkarga.domain.service;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralActKind;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;

/**
 * Rodzaje pism sędziego i składu (T-026, T-040): kod szablonu, czynność w aktach, podstawa prawna, zadanie procesu zamykane
 * po podpisie oraz zmienna terminu (D6). Wynik kontroli ({@code wynikKontroli}) jest ustawiany już przy sporządzeniu projektu.
 */
public enum LetterKind {

    ZARZ_BRAKI("Zarządzenie o usunięciu braków formalnych", ProceduralActKind.ZARZADZENIE, "BRAKI_FORMALNE", "art. 61 ust. 3",
            "T_Kontrola", ProcessVariables.WynikKontroli.BRAKI, "T_Braki", ProcessVariables.TERMIN_BRAKOW, CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW),
    POST_NADANIE_BIEGU("Postanowienie o nadaniu dalszego biegu", ProceduralActKind.POSTANOWIENIE, "NADANIE_BIEGU", "art. 61 ust. 1",
            "T_Kontrola", ProcessVariables.WynikKontroli.NADANIE_BIEGU, "T_Nadanie", null, null),
    POST_ODMOWA("Postanowienie o odmowie nadania dalszego biegu", ProceduralActKind.POSTANOWIENIE, "ODMOWA", "art. 61 ust. 4",
            "T_Kontrola", ProcessVariables.WynikKontroli.ODMOWA, "T_Odmowa", ProcessVariables.TERMIN_ZAZALENIA, CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE),
    POST_ZAZALENIE_BEZ_ROZPOZNANIA("Postanowienie o pozostawieniu zażalenia bez rozpoznania", ProceduralActKind.POSTANOWIENIE, "ZAZALENIE_BEZ_ROZPOZNANIA", "art. 61 ust. 5",
            null, null, "T_BezRozp", null, null),
    POST_ZAZALENIE_ROZSTRZYGNIECIE("Postanowienie w sprawie zażalenia", ProceduralActKind.POSTANOWIENIE, "ZAZALENIE_ROZSTRZYGNIECIE", "art. 61 ust. 6",
            null, null, "T_RozpZaz", null, null),
    ZAWIADOMIENIE_ART_63("Zawiadomienie o nadaniu dalszego biegu", ProceduralActKind.ZAWIADOMIENIE, "ART_63", "art. 63", null, null, null, null, null),
    ZARZ_WYZNACZENIE_SEDZIEGO("Zarządzenie Prezesa TK o wyznaczeniu sędziego", ProceduralActKind.ZARZADZENIE, "WYZNACZENIE_SEDZIEGO", "art. 61 ust. 1",
            null, null, null, null, null);

    private final String title;
    private final ProceduralActKind actKind;
    private final String subtype;
    private final String legalBasis;
    private final String decisionTaskKey;
    private final ProcessVariables.WynikKontroli reviewResult;
    private final String signTaskKey;
    private final String deadlineVariable;
    private final CaseStatus statusAfterSigning;

    LetterKind(String title, ProceduralActKind actKind, String subtype, String legalBasis, String decisionTaskKey, ProcessVariables.WynikKontroli reviewResult,
               String signTaskKey, String deadlineVariable, CaseStatus statusAfterSigning) {
        this.title = title;
        this.actKind = actKind;
        this.subtype = subtype;
        this.legalBasis = legalBasis;
        this.decisionTaskKey = decisionTaskKey;
        this.reviewResult = reviewResult;
        this.signTaskKey = signTaskKey;
        this.deadlineVariable = deadlineVariable;
        this.statusAfterSigning = statusAfterSigning;
    }

    public String title() { return title; }
    public ProceduralActKind actKind() { return actKind; }
    public String subtype() { return subtype; }
    public String legalBasis() { return legalBasis; }
    /** Zadanie, które projekt pisma rozstrzyga (T_Kontrola z wynikiem kontroli), albo {@code null}. */
    public String decisionTaskKey() { return decisionTaskKey; }
    public ProcessVariables.WynikKontroli reviewResult() { return reviewResult; }
    /** Zadanie zamykane po podpisie pisma (ruch procesu w tej samej transakcji, D10). */
    public String signTaskKey() { return signTaskKey; }
    public String deadlineVariable() { return deadlineVariable; }
    public CaseStatus statusAfterSigning() { return statusAfterSigning; }
}
