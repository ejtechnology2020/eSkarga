package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.PolishDates;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;

/**
 * Płaski model pisma z danych sprawy (D13): tylko teksty, liczby, flagi, listy i mapy; daty już po polsku.
 * Odmiana nazwisk (dopełniacz, biernik) pochodzi z karty sprawy, gdy ją uzupełniono; inaczej mianownik.
 */
public final class LetterModelBuilder {

    private LetterModelBuilder() {
    }

    public static Map<String, Object> build(CaseFile cf, List<Party> parties, LocalDate today, String judgeDisplayName, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean skarga = cf.getDocket().symbol() == DocketSymbol.TS;
        m.put("sygnatura", cf.getDocket().format());
        m.put("miejscowosc", "Warszawa");
        m.put("data", PolishDates.words(today));
        m.put("dataZarzadzenia", PolishDates.words(today));
        m.put("dataPostanowienia", PolishDates.words(today));
        m.put("dataWplywu", PolishDates.words(cf.getEnteredOn()));
        m.put("sedzia", judgeDisplayName == null ? "" : judgeDisplayName);
        m.put("rodzajPisma", skarga ? "skargi konstytucyjnej" : "wniosku");
        m.put("rodzajPismaMianownik", skarga ? "skarga konstytucyjna" : "wniosek");
        m.put("przedmiot", String.valueOf(cf.current().getReviewSubject()));
        m.put("wzorzec", String.valueOf(cf.current().getReviewStandard()));
        m.put("sygnaturaPowiazana", cf.current().getRelatedDocket() == null ? "" : cf.current().getRelatedDocket());
        Party complainant = parties.stream().filter(p -> p.getRole() == PartyRole.SKARZACY || p.getRole() == PartyRole.WNIOSKODAWCA).findFirst().orElse(null);
        Party attorney = parties.stream().filter(p -> p.getRole() == PartyRole.PELNOMOCNIK).findFirst().orElse(null);
        m.put("skarzacyMianownik", complainant == null ? "" : complainant.getDisplayName());
        m.put("skarzacy", complainant == null ? "" : complainant.getDisplayName());
        Party addressee = attorney != null ? attorney : complainant;
        m.put("adresatMianownik", addressee == null ? "" : addressee.getDisplayName());
        m.put("adresat", addressee == null ? "" : addressee.getDisplayName());
        m.put("adresAdresata", addressee == null || addressee.getDeliveryAddress() == null ? "" : addressee.getDeliveryAddress());
        m.put("pelnomocnik", attorney == null ? "" : attorney.getDisplayName());
        m.put("braki", new ArrayList<String>());
        m.put("uzasadnienie", new ArrayList<String>());
        m.put("sklad", new ArrayList<String>());
        m.put("przewodniczacy", "");
        m.put("podstawaOdmowy", 3);
        m.put("zazalenieZasadne", Boolean.FALSE);
        m.put("dataPosiedzenia", "");
        m.put("dataDoreczenia", "");
        m.put("odbiorcyZawiadomienia", new ArrayList<String>());
        if (extra != null) {
            m.putAll(extra);
        }
        return m;
    }
}
