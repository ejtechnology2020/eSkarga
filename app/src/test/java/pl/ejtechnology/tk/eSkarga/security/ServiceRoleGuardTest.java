package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;

import org.junit.jupiter.api.Test;

/**
 * Reguła architektoniczna (obrona w głąb, D4 poziom 1): każda usługa EJB w {@code domain.service} ma {@link RequiresRole} na klasie,
 * a każda publiczna metoda zmieniająca stan (po nazwie) – własną, jawnie przejrzaną regułę na metodzie. Usługi infrastrukturalne
 * (dziennik zdarzeń, liczniki sygnatur, polityka dostępu, rejestr osób, proces, wyszukiwanie) są poza kontrolą ról – wołane przed
 * ustaleniem ról albo bez użytkownika – i wymienione tu z nazwy, żeby dopisanie nowej usługi bez reguły nie przeszło niezauważone.
 */
class ServiceRoleGuardTest {

    static final Set<String> INTERNAL = Set.of(
            "pl.ejtechnology.tk.eSkarga.audit.AuditService",
            "pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService",
            "pl.ejtechnology.tk.eSkarga.domain.service.CaseAccessService",
            "pl.ejtechnology.tk.eSkarga.domain.service.UserDirectoryService",
            "pl.ejtechnology.tk.eSkarga.process.EskargaProcessApplication",
            "pl.ejtechnology.tk.eSkarga.process.FormalRequirementsService",
            "pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess",
            "pl.ejtechnology.tk.eSkarga.process.TaskBoardService",
            "pl.ejtechnology.tk.eSkarga.search.SearchService");

    /** Nazwy metod zmieniających stan: przedrostek i wielka litera albo koniec nazwy (żeby {@code addresseeFor} nie liczyło się jako {@code add}). */
    static final Pattern MUTATING = Pattern.compile("^(create|add|register|save|update|delete|withdraw|activate|approve|reject|revoke|assign|draft|finalize|dispatch|prepare|confirm|returned|correct|qualify|attach|upload|apply|mark|change|transfer|record|schedule|queue|decide|refer|request|begin|complete|seed|replied|anonymised|touch|index|rebuild|render)([A-Z]|$)");

    @Test
    void uslugiMajaReguleRolNaKlasieIMetodachZmieniajacychStan() throws IOException, ClassNotFoundException {
        Path classes = Path.of("target/classes");
        List<String> problemy = new ArrayList<>();
        List<String> sprawdzone = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class") && !f.toString().contains("$")).toList()) {
                String name = classes.relativize(p).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", "");
                Class<?> c = Class.forName(name, false, getClass().getClassLoader());
                if (!c.isAnnotationPresent(Stateless.class) && !c.isAnnotationPresent(Singleton.class)) {
                    continue;
                }
                if (INTERNAL.contains(name)) {
                    assertThat(c.isAnnotationPresent(RequiresRole.class)).as(name + " jest na liście INTERNAL, a ma @RequiresRole – usuń z listy").isFalse();
                    continue;
                }
                sprawdzone.add(c.getSimpleName());
                if (!c.isAnnotationPresent(RequiresRole.class)) {
                    problemy.add(c.getSimpleName() + " – brak @RequiresRole na klasie (albo wpisu w INTERNAL z uzasadnieniem)");
                }
                for (Method m : c.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (!Modifier.isPublic(mod) || Modifier.isStatic(mod) || m.isSynthetic() || m.isBridge()) {
                        continue;
                    }
                    if (MUTATING.matcher(m.getName()).find() && !m.isAnnotationPresent(RequiresRole.class)) {
                        problemy.add(c.getSimpleName() + "." + m.getName() + "(…) – metoda zmieniająca stan bez własnej reguły @RequiresRole");
                    }
                }
            }
        }
        assertThat(sprawdzone).as("usługi objęte kontrolą ról").hasSizeGreaterThanOrEqualTo(19);
        assertThat(problemy).as("usługi EJB bez reguły ról").isEmpty();
    }
}
