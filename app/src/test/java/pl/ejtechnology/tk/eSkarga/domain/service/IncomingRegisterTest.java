package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-01, UC-02: dziennik wpływu (T-011). */
class IncomingRegisterTest extends ServiceTestSupport {

    @Test
    void numeracjaDziennikaJestCiaglaWRamachRoku() {
        IncomingLetter a = tx(s -> registerLetter(s));
        IncomingLetter b = tx(s -> registerLetter(s));
        assertThat(a.getRegisterNo()).matches("DW 2026/\\d{5}");
        int na = Integer.parseInt(a.getRegisterNo().substring(8));
        int nb = Integer.parseInt(b.getRegisterNo().substring(8));
        assertThat(nb).isEqualTo(na + 1);
        assertThat(auditCount("rejestracja wpływu")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void pismoElektroniczneWymagaDatyZUpo() {
        assertThatThrownBy(() -> tx(s -> s.incoming.register(new IncomingRegister.RegisterCommand(IntakeChannel.EPUAP, Instant.now(), null, false,
                "Jan Przykładowy", 0, 1, false, "kprzykladowy", null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UPO");
    }

    @Test
    void dokumentZKlauzulaZapisujeTylkoMetadane() {
        IncomingLetter l = tx(s -> s.incoming.register(new IncomingRegister.RegisterCommand(IntakeChannel.OSOBISCIE, null, LocalDate.of(2026, 11, 3), false,
                "Jan Przykładowy", 1, 1, true, "kprzykladowy", null)));
        assertThat(l.isClassified()).isTrue();
        assertThat(auditCount("rejestracja wpływu (tylko metadane, klauzula)")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void kwalifikacjaJestJednorazowa() {
        long id = tx(s -> registerLetter(s)).getId();
        run(s -> s.incoming.qualify(id, LetterQualification.PISMO_OGOLNE, null, REFERENT));
        assertThatThrownBy(() -> run(s -> s.incoming.qualify(id, LetterQualification.PISMO_OGOLNE, null, REFERENT)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tx(s -> s.incoming.queue()).stream().map(IncomingLetter::getId)).doesNotContain(id);
    }

    @Test
    void skanPdfLubTiffTrafiaDoRepozytoriumZeSkrotem() {
        IncomingLetter l = tx(s -> registerLetter(s));
        byte[] pdf = "%PDF-1.7 skan testowy".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        var scan = tx(s -> s.incoming.attachScan(l.getId(), "pismo.pdf", pdf, "kprzykladowy", "10.0.0.7"));
        assertThat(scan.getContentType()).isEqualTo("application/pdf");
        assertThat(scan.getSha256()).hasSize(64);
        assertThat(scan.getStorageKey()).startsWith("wplyw/" + l.getId() + "/").endsWith(".pdf");
        assertThat(store.verifySha256(scan.getStorageKey(), scan.getSha256())).isTrue();
        byte[] tiff = new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0};
        tx(s -> s.incoming.attachScan(l.getId(), "koperta.tif", tiff, "kprzykladowy", null));
        java.util.List<pl.ejtechnology.tk.eSkarga.domain.model.IncomingScan> skany = tx(s -> s.incoming.scans(l.getId()));
        assertThat(skany).hasSize(2);
        assertThatThrownBy(() -> tx(s -> s.incoming.attachScan(l.getId(), "makro.docx", "PK\u0003\u0004".getBytes(), "kprzykladowy", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PDF lub TIFF");
        assertThat(auditCount("skan pisma dołączony (application/pdf, " + pdf.length + " B)")).isEqualTo(1);
    }

    @Test
    void pismoZKlauzulaNiePrzyjmujeSkanu() {
        IncomingLetter l = tx(s -> s.incoming.register(new IncomingRegister.RegisterCommand(IntakeChannel.OSOBISCIE, null, LocalDate.of(2026, 11, 3), false,
                "Jan Przykładowy", 1, 1, true, "kprzykladowy", null)));
        assertThatThrownBy(() -> tx(s -> s.incoming.attachScan(l.getId(), "x.pdf", "%PDF-1.4".getBytes(), "kprzykladowy", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("klauzul");
    }
}
