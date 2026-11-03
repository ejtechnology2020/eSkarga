package pl.ejtechnology.tk.eSkarga.docs.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.docs.support.TestPdfs;

/** FR-22, FR-27 (T-036): zanonimizowana kopia nie zawiera usuniętych danych; anonimizacja tekstu. */
class RedactionTest {

    @Test
    void zaczernienieUsuwaWarstweTekstowa() throws Exception {
        byte[] pdf = TestPdfs.withText("Jan Przykladowy", "PESEL 90010112345", "ul. Przykladowa 1, 00-001 Warszawa");
        assertThat(TestPdfs.text(pdf)).contains("90010112345");
        byte[] redacted = new PdfRedactor(100f).redact(pdf, List.of(new PdfRedactor.Box(1, 60, 700, 300, 60)));
        assertThat(TestPdfs.pages(redacted)).isEqualTo(1);
        assertThat(TestPdfs.text(redacted).trim()).as("wynik jest rastrowy – brak jakiegokolwiek tekstu").isEmpty();
        assertThat(new String(redacted, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("90010112345").doesNotContain("Przykladowy");
    }

    @Test
    void anonimizacjaTekstuZastepujeDaneOsobowe() {
        Anonymizer a = new Anonymizer(List.of("Jan Przykładowy", "Anna Testowa"));
        Anonymizer.Result r = a.anonymize("Skarżący Jan Przykładowy (PESEL 90010112345, dowód ABC 123456), zam. ul. Przykładowa 1/3, 00-001 Warszawa, "
                + "reprezentowany przez adw. Annę Testową, e-mail jan@example.test, tel. 600 700 800. Pełnomocnik Jana Przykładowego wniósł zażalenie.");
        assertThat(r.text()).contains("J.P.").contains("A.T.").contains("[PESEL]").contains("[nr dokumentu]").contains("[adres]").contains("[e-mail]").contains("[telefon]")
                .doesNotContain("90010112345").doesNotContain("Przykładow").doesNotContain("Testow").doesNotContain("example.test");
        assertThat(r.replacements()).isGreaterThanOrEqualTo(7);
        assertThat(r.kinds()).contains("nazwisko", "PESEL", "adres");
        assertThat(Anonymizer.stem("Przykładowego")).isEqualTo("Przykładow");
        assertThat(Anonymizer.stem("Testową")).isEqualTo("Testow");
        assertThat(a.anonymize("").replacements()).isZero();
    }
}
