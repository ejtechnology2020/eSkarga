package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** T-037 / D12: żaden klucz prywatny ani sekret nie występuje w plikach repozytorium (skan tekstowy źródeł i konfiguracji). */
class RepositorySecretsScanTest {

    private static final Pattern SECRETS = Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|client_secret\\s*[=:]\\s*['\"][A-Za-z0-9]{16,}");
    private static final Set<String> EXT = Set.of(".java", ".xml", ".yml", ".yaml", ".properties", ".cli", ".sh", ".env", ".json", ".ftl", ".xhtml", ".md", ".sql", ".cfg", ".ts", ".js");

    @Test
    void brakKluczyPrywatnychWRepozytorium() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/target/") && !p.toString().contains("/.git/") && !p.toString().contains("/node_modules/"))
                    .filter(p -> EXT.stream().anyMatch(e -> p.toString().endsWith(e)))
                    .forEach(p -> {
                        try {
                            String s = Files.readString(p, StandardCharsets.UTF_8);
                            if (SECRETS.matcher(s).find() && !p.getFileName().toString().equals("RepositorySecretsScanTest.java")) {
                                hits.add(root.relativize(p).toString());
                            }
                        } catch (IOException | RuntimeException ignored) {
                            // pliki binarne / inne kodowanie
                        }
                    });
        }
        assertThat(hits).as("pliki z kluczem prywatnym albo sekretem").isEmpty();
        // .env istnieje na każdej stacji dev (tworzą go skrypty) – nie może tylko trafić do Gita: wpis w .gitignore i brak w indeksie
        assertThat(Files.readString(root.resolve(".gitignore"))).as("infra/docker/.env w .gitignore").contains("infra/docker/.env");
        Path index = root.resolve(".git/index");
        if (Files.exists(index)) {
            String idx = new String(Files.readAllBytes(index), StandardCharsets.ISO_8859_1);
            assertThat(idx).as(".env śledzony przez Git").doesNotContain("infra/docker/.env\0");
            assertThat(idx).as("klucze podpisu dev śledzone przez Git").doesNotContain("infra/docker/.local/");
        }
    }
}
