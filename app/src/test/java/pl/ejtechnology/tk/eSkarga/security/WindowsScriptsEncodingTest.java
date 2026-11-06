package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Skrypty dla Windows: Windows PowerShell 5.1 czyta plik .ps1 bez znacznika BOM w stronie kodowej ANSI, a polskie znaki i półpauza
 * w tekstach rozsypują wtedy parser („Missing argument in parameter list”, „string is missing the terminator”). Każdy .ps1 ma
 * zaczynać się od BOM UTF-8; pliki .cmd (cmd.exe, OEM 852) tylko ASCII.
 */
class WindowsScriptsEncodingTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void skryptyPowerShellMajaBomAPlikiCmdTylkoAscii() throws IOException {
        Path dir = Path.of("..", "infra", "windows").toAbsolutePath().normalize();
        List<String> bezBom = new ArrayList<>();
        List<String> nieAscii = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(".ps1")) {
                    byte[] head = new byte[3];
                    try (var in = Files.newInputStream(p)) {
                        int n = in.read(head);
                        if (n < 3 || head[0] != BOM[0] || head[1] != BOM[1] || head[2] != BOM[2]) {
                            bezBom.add(name);
                        }
                    }
                } else if (name.endsWith(".cmd")) {
                    if (!StandardCharsets.US_ASCII.newEncoder().canEncode(Files.readString(p, StandardCharsets.UTF_8))) {
                        nieAscii.add(name);
                    }
                }
            }
        }
        assertThat(bezBom).as("skrypty .ps1 bez BOM UTF-8 (Windows PowerShell 5.1 czyta je jako ANSI)").isEmpty();
        assertThat(nieAscii).as("pliki .cmd ze znakami spoza ASCII").isEmpty();
    }
}
