package pl.ejtechnology.tk.eSkarga.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Jakarta EL 6 (WildFly 41) rozwiązuje właściwości rekordów przez {@code RecordELResolver}, który zna tylko komponenty rekordu:
 * {@code #{pg.niejawny}} na rekordzie z metodą {@code isNiejawny()} kończy się {@code PropertyNotFoundException} (S-01, S-14, 06.11.2026).
 * Takie metody wywołujemy w stronach składnią metody: {@code #{pg.isNiejawny()}}. Test pilnuje, by żadna strona nie używała
 * składni właściwości dla metod rekordów modeli widoków, które nie są komponentami.
 */
class RecordAccessorsInElTest {

    private static final List<Class<?>> RECORDS = List.of(CaseSummary.class, RecordPage.class, TimelineEntry.class, KpiTile.class, TaskRow.class);

    /** Wyrażenia na beanach (nie rekordach) o tej samej nazwie właściwości – dozwolone. */
    private static final Set<String> DOZWOLONE = Set.of("akta.readOnly", "edytor.readOnly", "status.readOnly", "common.readOnly");

    @Test
    void stronyNieUzywajaSkladniWlasciwosciDlaMetodRekordow() throws IOException {
        List<String> problemy = new ArrayList<>();
        for (Class<?> r : RECORDS) {
            assertThat(r.isRecord()).as(r.getSimpleName() + " jest rekordem").isTrue();
            Set<String> komponenty = Stream.of(r.getRecordComponents()).map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
            for (Method m : r.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) {
                    continue;
                }
                String n = m.getName();
                String prop = n.startsWith("is") && n.length() > 2 ? Character.toLowerCase(n.charAt(2)) + n.substring(3)
                        : n.startsWith("get") && n.length() > 3 ? Character.toLowerCase(n.charAt(3)) + n.substring(4) : null;
                if (prop == null || komponenty.contains(prop)) {
                    continue;
                }
                Pattern wzor = Pattern.compile("([A-Za-z_][\\w.]*)\\." + Pattern.quote(prop) + "\\b(?!\\s*\\()");
                try (Stream<Path> strony = Files.walk(Path.of("src/main/webapp"))) {
                    for (Path p : strony.filter(x -> x.toString().endsWith(".xhtml")).toList()) {
                        Matcher mm = wzor.matcher(Files.readString(p));
                        while (mm.find()) {
                            String wyr = mm.group(1) + "." + prop;
                            if (DOZWOLONE.stream().noneMatch(wyr::endsWith)) {
                                problemy.add(p + ": #{…" + wyr + "} – " + r.getSimpleName() + "." + n + "() nie jest komponentem rekordu; w EL użyj składni metody: ." + n + "()");
                            }
                        }
                    }
                }
            }
        }
        assertThat(problemy).as("składnia właściwości na metodach rekordów (RecordELResolver)").isEmpty();
    }
}
