package pl.ejtechnology.tk.eSkarga.docs.ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/**
 * OCRmyPDF z linii poleceń ({@code ocrmypdf -l pol --skip-text --output-type pdfa-2}). Pliki tymczasowe w katalogu
 * {@code eskarga.docs.workDir} z prawami tylko dla usługi; usuwane po przetworzeniu. Limit czasu konfigurowalny.
 */
public final class OcrMyPdfCli implements OcrService {

    private final String binary;
    private final Path workDir;
    private final long timeoutSeconds;

    public OcrMyPdfCli() {
        this(Settings.value("eskarga.docs.ocrmypdf", "ocrmypdf"), Path.of(Settings.value("eskarga.docs.workDir", System.getProperty("java.io.tmpdir"))),
                Long.parseLong(Settings.value("eskarga.docs.ocrTimeoutSeconds", "600")));
    }

    public OcrMyPdfCli(String binary, Path workDir, long timeoutSeconds) {
        this.binary = binary;
        this.workDir = workDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean available() {
        try {
            Process p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public Result ocr(byte[] pdf) {
        Path in = null;
        Path out = null;
        try {
            Files.createDirectories(workDir);
            in = Files.createTempFile(workDir, "ocr-in-", ".pdf");
            out = Files.createTempFile(workDir, "ocr-out-", ".pdf");
            Files.write(in, pdf);
            Process p = new ProcessBuilder(List.of(binary, "-l", "pol", "--skip-text", "--output-type", "pdfa-2", "--quiet",
                    in.toAbsolutePath().toString(), out.toAbsolutePath().toString())).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(pdf, false, "OCR przerwany po " + timeoutSeconds + " s");
            }
            if (p.exitValue() != 0) {
                return new Result(pdf, false, "OCR zakończony błędem " + p.exitValue() + ": " + log.lines().findFirst().orElse(""));
            }
            return new Result(Files.readAllBytes(out), true, "OCR: ocrmypdf, język pol");
        } catch (IOException e) {
            return new Result(pdf, false, "OCR niedostępny: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(pdf, false, "OCR przerwany");
        } finally {
            delete(in);
            delete(out);
        }
    }

    private static void delete(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // plik tymczasowy; katalog roboczy jest czyszczony przez systemd-tmpfiles
            }
        }
    }
}
