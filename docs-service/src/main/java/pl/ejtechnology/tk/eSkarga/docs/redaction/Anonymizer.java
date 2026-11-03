package pl.ejtechnology.tk.eSkarga.docs.redaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonimizacja tekstu (metadane, treść OCR do publikacji; FR-22, FR-27): PESEL, numery dokumentów, adresy, nazwiska
 * z listy stron zastępowane inicjałami. Wynik przegląda człowiek przed publikacją (UC-16).
 */
public final class Anonymizer {

    /** Wynik: tekst i liczba zamian (do raportu anonimizacji). */
    public record Result(String text, int replacements, List<String> kinds) {
    }

    private static final Pattern PESEL = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("\\b[A-Z]{3}\\s?\\d{6}\\b");
    private static final Pattern ADDRESS = Pattern.compile("(?i)\\b(ul\\.|al\\.|pl\\.|os\\.)\\s+[\\p{L}\\d .\\-]+?\\s+\\d+[a-zA-Z]?(?:/\\d+[a-zA-Z]?)?(?:,\\s*\\d{2}-\\d{3}\\s+[\\p{L}\\- ]+)?");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+48\\s?)?\\d{3}[ -]?\\d{3}[ -]?\\d{3}(?!\\d)");

    private final List<String> names;

    /** @param personNames pełne imiona i nazwiska stron (w mianowniku) – także odmiany są wykrywane po rdzeniu nazwiska */
    public Anonymizer(List<String> personNames) {
        this.names = personNames == null ? List.of() : personNames;
    }

    public Result anonymize(String text) {
        if (text == null || text.isBlank()) {
            return new Result(text == null ? "" : text, 0, List.of());
        }
        int count = 0;
        List<String> kinds = new ArrayList<>();
        String out = text;
        for (String name : names) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String initials = Character.toUpperCase(parts[0].charAt(0)) + "." + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
            String surnameStem = stem(parts[parts.length - 1]);
            Pattern p = Pattern.compile("\\b" + Pattern.quote(parts[0].substring(0, Math.max(3, parts[0].length() - 2))) + "\\p{L}*\\s+" + Pattern.quote(surnameStem) + "\\p{L}*\\b", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(out);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(initials));
                n++;
            }
            m.appendTail(sb);
            if (n > 0) {
                out = sb.toString();
                count += n;
                kinds.add("nazwisko");
            }
        }
        for (var e : List.of(new Object[]{PESEL, "[PESEL]", "PESEL"}, new Object[]{ID_CARD, "[nr dokumentu]", "dokument"},
                new Object[]{EMAIL, "[e-mail]", "e-mail"}, new Object[]{PHONE, "[telefon]", "telefon"}, new Object[]{ADDRESS, "[adres]", "adres"})) {
            Matcher m = ((Pattern) e[0]).matcher(out);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement((String) e[1]));
                n++;
            }
            m.appendTail(sb);
            if (n > 0) {
                out = sb.toString();
                count += n;
                kinds.add((String) e[2]);
            }
        }
        return new Result(out, count, List.copyOf(kinds));
    }

    /** Rdzeń nazwiska do dopasowania odmian (Przykładowy → Przykładow, Testowa → Testow). */
    static String stem(String surname) {
        String s = surname.toLowerCase(Locale.ROOT);
        for (String suffix : List.of("iego", "ego", "emu", "ym", "im", "ich", "ych", "ej", "ą", "a", "y", "i", "e")) {
            if (s.endsWith(suffix) && s.length() - suffix.length() >= 4) {
                return surname.substring(0, surname.length() - suffix.length());
            }
        }
        return surname;
    }
}
