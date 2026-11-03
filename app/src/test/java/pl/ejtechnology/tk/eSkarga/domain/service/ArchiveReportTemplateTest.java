package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.ArchiveTransfer;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;
import pl.ejtechnology.tk.eSkarga.domain.model.GeneralLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.LetterTemplate;
import pl.ejtechnology.tk.eSkarga.domain.model.ReviewStandard;
import pl.ejtechnology.tk.eSkarga.domain.model.TemplateStatus;
import pl.ejtechnology.tk.eSkarga.support.InMemoryDocumentStore;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-17, UC-19, UC-21, UC-24, FR-30 – FR-35: pisma ogólne, raporty, archiwum, szablony, słowniki (T-026a, T-050 – T-052). */
class ArchiveReportTemplateTest extends ServiceTestSupport {

    private CaseFile closedCase(ClosingMode mode, LocalDate closedOn, boolean withPdfa) {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            CaseDocument skarga = s.cases.documents(loaded).get(0);
            byte[] pdf = ("%PDF-1.7 fikcyjna skarga " + cf.getDocket().format()).getBytes(StandardCharsets.UTF_8);
            var stored = store.put("akta/" + cf.getId() + "/0001.pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf", true);
            skarga.attachFile(stored.key(), stored.sha256());
            skarga.setPdfaCompliant(withPdfa);
            CaseDocument notatka = new CaseDocument(loaded, "Notatka wewnętrzna", DocumentKind.NOTATKA, TODAY, "asystent");
            notatka.paginate(1, 15, 15);
            byte[] note = "notatka".getBytes(StandardCharsets.UTF_8);
            var ns = store.put("akta/" + cf.getId() + "/notatka.pdf", new ByteArrayInputStream(note), note.length, "application/pdf", false);
            notatka.attachFile(ns.key(), ns.sha256());
            s.em.persist(notatka);
            CaseFileVersion v = s.cases.changeStatus(cf.getId(), mode == ClosingMode.NADANIE_BIEGU ? CaseStatus.ZAKONCZONA_NADANO_BIEG : CaseStatus.ZAKONCZONA_ODMOWA_PRAWOMOCNA, "sedzia");
            v.setClosingMode(mode);
            v.setClosedOn(closedOn);
            if (mode == ClosingMode.NADANIE_BIEGU) {
                v.setRelatedDocket("SK 9/26");
            }
        });
        return cf;
    }

    @Test
    void paczkaArchiwalnaZawieraMetadaneSumyIDokumentyJawneBezNotatek() throws IOException {
        CaseFile cf = closedCase(ClosingMode.ODMOWA_PRAWOMOCNA, LocalDate.of(2026, 10, 20), true);
        run(s -> assertThat(s.archive.closedNotArchived()).extracting(CaseFile::getId).contains(cf.getId()));
        ArchiveTransfer t = tx(s -> s.archive.transfer(cf.getId(), "archiwista"));
        assertThat(t.getTransferNo()).matches("\\d+/2026");
        assertThat(t.getCategory()).isEqualTo("B10");
        assertThat(t.getRetentionUntil()).isEqualTo(2036);
        assertThat(t.getPages()).as("karty akt jawnych – bez notatki wewnętrznej").isEqualTo(14);

        InMemoryDocumentStore.Entry pkg = store.files().get(t.getPackageKey());
        assertThat(pkg.locked()).as("Object Lock compliance").isTrue();
        assertThat(InMemoryDocumentStore.sha256(pkg.bytes())).isEqualTo(t.getPackageSha256());
        Map<String, byte[]> entries = unzip(pkg.bytes());
        assertThat(entries.keySet()).containsExactlyInAnyOrder("dokumenty/0001.pdf", "metadata.xml", "SHA256SUMS");
        String meta = new String(entries.get("metadata.xml"), StandardCharsets.UTF_8);
        assertThat(meta).contains("<sygnatura>" + cf.getDocket().format() + "</sygnatura>").contains("B10").doesNotContain("Notatka wewnętrzna");
        String sums = new String(entries.get("SHA256SUMS"), StandardCharsets.UTF_8);
        assertThat(sums).contains(InMemoryDocumentStore.sha256(entries.get("dokumenty/0001.pdf")) + "  dokumenty/0001.pdf")
                .contains(InMemoryDocumentStore.sha256(entries.get("metadata.xml")) + "  metadata.xml");
        run(s -> assertThat(s.archive.closedNotArchived()).extracting(CaseFile::getId).doesNotContain(cf.getId()));
        run(s -> assertThat(s.archive.yearlyLedgerXhtml("Ts", 2026)).contains(cf.getDocket().format()).contains("ODMOWA_PRAWOMOCNA"));
    }

    @Test
    void brakPdfaAlboNadanieBieguBlokujePrzekazanie() {
        CaseFile bezPdfa = closedCase(ClosingMode.BEZ_ROZPOZNANIA, LocalDate.of(2026, 10, 21), false);
        assertThatThrownBy(() -> tx(s -> s.archive.transfer(bezPdfa.getId(), "archiwista"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("PDF/A");
        run(s -> assertThat(s.archive.readiness(s.cases.require(bezPdfa.getId())).missingPdfa()).hasSize(1));
        CaseFile nadanie = closedCase(ClosingMode.NADANIE_BIEGU, LocalDate.of(2026, 10, 22), true);
        assertThatThrownBy(() -> tx(s -> s.archive.transfer(nadanie.getId(), "archiwista"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("SK 9/26");
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile open = tx(s -> createCase(s, letterId));
        assertThatThrownBy(() -> tx(s -> s.archive.transfer(open.getId(), "archiwista"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("nie jest zakończona");
    }

    @Test
    void raportLiczyWplywZalatwieniaIOdsetekNadaniaBiegu() {
        closedCase(ClosingMode.ODMOWA_PRAWOMOCNA, LocalDate.of(2026, 11, 2), true);
        closedCase(ClosingMode.NADANIE_BIEGU, LocalDate.of(2026, 11, 3), true);
        long letterId = tx(s -> registerLetter(s)).getId();
        tx(s -> createCase(s, letterId));
        ReportService.Summary sum = tx(s -> s.reports.summary("Ts", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        assertThat(sum.intake()).isGreaterThanOrEqualTo(3);
        assertThat(sum.disposed()).isGreaterThanOrEqualTo(2);
        assertThat(sum.byMode().get(ClosingMode.NADANIE_BIEGU)).isGreaterThanOrEqualTo(1);
        assertThat(sum.admissionRatio()).isBetween(0.0, 100.0);
        assertThat(sum.intakeByMonth()).containsKey(11);
        assertThat(sum.medianDays()).isLessThanOrEqualTo(sum.p90Days());
        run(s -> {
            assertThat(s.reports.countByStatus(CaseStatus.ZAREJESTROWANA)).isGreaterThanOrEqualTo(1);
            assertThat(s.reports.backlog(0, TODAY.plusDays(1))).isNotEmpty();
            assertThat(s.reports.appealsUpheldRatio()).isEqualTo(0.0);
        });
        assertThat(ReportService.percentile(List.of(5L, 1L, 9L, 3L), 50)).isEqualTo(3L);
        assertThat(ReportService.percentile(List.of(5L, 1L, 9L, 3L), 90)).isEqualTo(9L);
    }

    @Test
    void eksportCsvIXlsx() throws IOException {
        List<String> header = List.of("Sygnatura", "Status");
        List<List<String>> rows = List.of(List.of("Ts 312/26", "we wstępnej kontroli"), List.of("Tw 7/26", "zakończona; \"odmowa\""));
        String csv = new String(TabularExport.csv(header, rows), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF").contains("Ts 312/26;we wstępnej kontroli").contains("\"zakończona; \"\"odmowa\"\"\"");
        Map<String, byte[]> xlsx = unzip(TabularExport.xlsx("Repertorium", header, rows));
        assertThat(xlsx.keySet()).contains("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
        assertThat(new String(xlsx.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8)).contains("Ts 312/26");
    }

    @Test
    void szablonAktywujeDrugaOsobaPoWalidacji() {
        LetterTemplate v1 = tx(s -> s.templates.saveDraft("ZARZ_BRAKI", "Zarządzenie o usunięciu braków", "<html><body>${sygnatura}</body></html>", "autor"));
        assertThat(v1.getVersion()).isEqualTo(1);
        assertThatThrownBy(() -> tx(s -> s.templates.activate(v1.getId(), "autor"))).isInstanceOf(IllegalArgumentException.class);
        TemplateService.Validation ok = tx(s -> s.templates.activate(v1.getId(), "zatwierdzajacy"));
        assertThat(ok.ok()).isTrue();
        run(s -> assertThat(s.templates.active("ZARZ_BRAKI")).map(LetterTemplate::getVersion).contains(1));

        LetterTemplate v2 = tx(s -> s.templates.saveDraft("ZARZ_BRAKI", "Zarządzenie o usunięciu braków", "<html><script>alert(1)</script></html>", "autor"));
        TemplateService.Validation bad = tx(s -> s.templates.activate(v2.getId(), "zatwierdzajacy"));
        assertThat(bad.ok()).isFalse();
        run(s -> {
            assertThat(s.templates.active("ZARZ_BRAKI")).map(LetterTemplate::getVersion).as("błąd walidacji nie zmienia aktywnej wersji").contains(1);
            assertThat(s.em.find(LetterTemplate.class, v2.getId()).getStatus()).isEqualTo(TemplateStatus.PROJEKT);
        });
        LetterTemplate v3 = tx(s -> s.templates.saveDraft("ZARZ_BRAKI", "Zarządzenie o usunięciu braków", "<html><body>v3</body></html>", "autor"));
        tx(s -> s.templates.activate(v3.getId(), "zatwierdzajacy"));
        run(s -> {
            assertThat(s.templates.active("ZARZ_BRAKI")).map(LetterTemplate::getVersion).contains(3);
            assertThat(s.em.find(LetterTemplate.class, v1.getId()).getStatus()).isEqualTo(TemplateStatus.WYCOFANA);
            assertThat(s.templates.all()).hasSizeGreaterThanOrEqualTo(3);
        });
        assertThat(auditCount("aktywacja szablonu")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void pismoOgolneDostajeTerminOdpowiedzi30Dni() {
        long letterId = tx(s -> registerLetter(s)).getId();
        GeneralLetter g = tx(s -> s.generalLetters.register(letterId, "Prośba o informację o stanie sprawy", REFERENT));
        assertThat(g.getReplyDueOn()).isEqualTo(LocalDate.of(2026, 10, 30).plusDays(30));
        run(s -> assertThat(s.generalLetters.unanswered()).extracting(GeneralLetter::getId).contains(g.getId()));
        run(s -> s.generalLetters.replied(g.getId(), "ODP_OGOLNA", REFERENT));
        run(s -> {
            assertThat(s.generalLetters.unanswered()).extracting(GeneralLetter::getId).doesNotContain(g.getId());
            assertThat(s.em.find(GeneralLetter.class, g.getId()).getRepliedOn()).isEqualTo(TODAY);
        });
    }

    @Test
    void slownikiWycofujaZamiastUsuwac() {
        ReviewStandard st = tx(s -> s.dictionaries.add(new ReviewStandard("K-45-1", "art. 45 ust. 1 Konstytucji", "prawo do sądu"), "admin"));
        run(s -> assertThat(s.dictionaries.reviewStandards()).extracting(ReviewStandard::getCode).contains("K-45-1"));
        run(s -> s.dictionaries.withdrawReviewStandard(st.getId(), "admin"));
        run(s -> assertThat(s.em.find(ReviewStandard.class, st.getId()).isActive()).isFalse());
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                out.put(e.getName(), in.readAllBytes());
            }
        }
        return out;
    }
}
