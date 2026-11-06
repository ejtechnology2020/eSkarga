package pl.ejtechnology.tk.eSkarga.docs.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.ws.rs.ApplicationPath;

import org.junit.jupiter.api.Test;

/** Adres usługi dokumentów jest umową z aplikacją i workerami ({@code /eskarga-docs/api}) – kontekst WAR-a nie może zależeć od nazwy pliku. */
class ContextRootTest {

    @Test
    void kontekstWarUslugiDokumentowToEskargaDocs() throws Exception {
        Path jbossWeb = Path.of("src/main/webapp/WEB-INF/jboss-web.xml");
        assertThat(jbossWeb).exists();
        assertThat(Files.readString(jbossWeb)).contains("<context-root>/eskarga-docs</context-root>");
        assertThat(DocsApplication.class.getAnnotation(ApplicationPath.class).value()).isEqualTo("/api");
    }
}
