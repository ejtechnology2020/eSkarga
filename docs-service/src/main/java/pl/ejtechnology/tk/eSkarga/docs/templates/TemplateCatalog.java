package pl.ejtechnology.tk.eSkarga.docs.templates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domyślne szablony pism (T-026) dostarczane z usługą; aplikacja zapisuje je jako wersję 1 w tabeli {@code letter_template}
 * i od tej chwili źródłem prawdy jest baza (D13). Kody są stałe – odwołuje się do nich proces i ekran S-11.
 */
public final class TemplateCatalog {

    /** Kod szablonu → plik w {@code /templates}. */
    public static final Map<String, String> FILES = new LinkedHashMap<>();

    static {
        FILES.put("ZARZ_WYZNACZENIE_SEDZIEGO", "zarzadzenie-o-wyznaczeniu-sedziego.xhtml.ftl");
        FILES.put("ZARZ_BRAKI", "zarzadzenie-o-usunieciu-brakow.xhtml.ftl");
        FILES.put("POST_NADANIE_BIEGU", "postanowienie-o-nadaniu-biegu.xhtml.ftl");
        FILES.put("POST_ODMOWA", "postanowienie-o-odmowie.xhtml.ftl");
        FILES.put("POST_ZAZALENIE_BEZ_ROZPOZNANIA", "postanowienie-zazalenie-bez-rozpoznania.xhtml.ftl");
        FILES.put("POST_ZAZALENIE_ROZSTRZYGNIECIE", "postanowienie-o-zazaleniu.xhtml.ftl");
        FILES.put("ZAWIADOMIENIE_ART_63", "zawiadomienie-art-63.xhtml.ftl");
        FILES.put("ODP_PISMO_OGOLNE", "odpowiedz-na-pismo-ogolne.xhtml.ftl");
    }

    private TemplateCatalog() {
    }

    public static List<String> codes() {
        return List.copyOf(FILES.keySet());
    }

    public static String source(String code) {
        String file = FILES.get(code);
        if (file == null) {
            throw new IllegalArgumentException("Nieznany szablon: " + code);
        }
        try (InputStream in = TemplateCatalog.class.getResourceAsStream("/templates/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Brak pliku szablonu " + file);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
