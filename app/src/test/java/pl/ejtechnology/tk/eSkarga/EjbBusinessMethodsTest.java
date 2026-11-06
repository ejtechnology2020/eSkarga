package pl.ejtechnology.tk.eSkarga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;

import org.junit.jupiter.api.Test;

/**
 * Usługi EJB są wołane przez proxy kontenera: metoda pakietowa wywołana z innej klasy kończy się w WildFly błędem
 * „Not a business method … Do not call non-public methods on EJB's” (S-17, 06.11.2026 – {@code CaseFileService.lastPage}).
 * Testy jednostkowe wiążą usługi bez proxy (Wiring), więc tego nie wykrywają – stąd reguła: na klasach {@code @Stateless}/{@code @Singleton}
 * metody instancyjne są publiczne albo prywatne, nigdy pakietowe/chronione.
 */
class EjbBusinessMethodsTest {

    @Test
    void uslugiEjbNieMajaMetodPakietowych() throws IOException, ClassNotFoundException {
        Path classes = Path.of("target/classes");
        List<String> problemy = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class") && !f.toString().contains("$")).toList()) {
                String name = classes.relativize(p).toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", "");
                Class<?> c = Class.forName(name, false, getClass().getClassLoader());
                if (!c.isAnnotationPresent(Stateless.class) && !c.isAnnotationPresent(Singleton.class)) {
                    continue;
                }
                for (Method m : c.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isPublic(mod) || Modifier.isPrivate(mod) || m.isSynthetic() || m.isBridge()) {
                        continue;
                    }
                    problemy.add(c.getSimpleName() + "." + m.getName() + "(…) – metoda " + (Modifier.isProtected(mod) ? "chroniona" : "pakietowa") + " na EJB");
                }
            }
        }
        assertThat(problemy).as("metody niepubliczne na usługach EJB (kontener odrzuca ich wywołanie przez proxy)").isEmpty();
    }
}
