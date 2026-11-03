package pl.ejtechnology.tk.eSkarga.docs.templates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model danych pisma na danych fikcyjnych (sprawa Ts 312/26) do podglądu i walidacji szablonów w edytorze (S-24, T-026a).
 * Płaskie mapy i listy, daty już sformatowane po polsku (D13). Zawiera wszystkie klucze używane przez szablony domyślne.
 */
public final class SampleLetterModel {

    private SampleLetterModel() {
    }

    public static Map<String, Object> ts312() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sygnatura", "Ts 312/26");
        m.put("sygnaturaPowiazana", "SK 9/26");
        m.put("miejscowosc", "Warszawa");
        m.put("data", "3 listopada 2026");
        m.put("dataZarzadzenia", "3 listopada 2026");
        m.put("dataPostanowienia", "3 listopada 2026");
        m.put("dataWplywu", "28 października 2026");
        m.put("dataDoreczenia", "6 listopada 2026");
        m.put("sedzia", "Piotr Fikcyjny");
        m.put("sklad", List.of("Piotr Fikcyjny", "Maria Przykładowa", "Tomasz Testowy"));
        m.put("przewodniczacy", "Piotr Fikcyjny");
        m.put("skarzacy", "Jana Przykładowego");
        m.put("skarzacyMianownik", "Jan Przykładowy");
        m.put("adresat", "adw. Annę Testową");
        m.put("adresatMianownik", "adw. Anna Testowa");
        m.put("adresAdresata", "ul. Testowa 2, 00-002 Warszawa");
        m.put("pelnomocnik", "adw. Anna Testowa");
        m.put("przedmiot", "art. 5 ust. 2 ustawy z dnia 1 stycznia 2020 r. o przykładach (Dz. U. poz. 1)");
        m.put("wzorzec", "art. 45 ust. 1 Konstytucji");
        m.put("rodzajPisma", "skargi konstytucyjnej");
        m.put("rodzajPismaMianownik", "skarga konstytucyjna");
        m.put("podstawaOdmowy", 3);
        m.put("uzasadnienie", List.of(
                "Skarżący nie usunął w terminie braków formalnych wskazanych w zarządzeniu z dnia 6 listopada 2026 r.",
                "Zgodnie z art. 61 ust. 4 pkt 3 ustawy Trybunał odmawia nadania skardze dalszego biegu."));
        m.put("braki", List.of(
                "pełnomocnictwo szczególne do sporządzenia skargi konstytucyjnej (art. 53 ust. 2 pkt 3)",
                "wskazanie sposobu naruszenia konstytucyjnego prawa do sądu (art. 53 ust. 1 pkt 2)"));
        m.put("zazalenieWTerminie", true);
        m.put("zazalenieZasadne", false);
        m.put("dataPosiedzenia", "20 listopada 2026");
        m.put("odbiorcyZawiadomienia", List.of("Sejm Rzeczypospolitej Polskiej", "Prokurator Generalny", "Rzecznik Praw Obywatelskich"));
        m.put("tematPisma", "prośba o informację o wymogach skargi konstytucyjnej");
        m.put("numerDziennika", "DW 2026/04812");
        m.put("referent", "Katarzyna Przykładowa");
        return m;
    }
}
