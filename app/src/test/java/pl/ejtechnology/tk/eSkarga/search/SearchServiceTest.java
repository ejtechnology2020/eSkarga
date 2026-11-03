package pl.ejtechnology.tk.eSkarga.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.support.InMemoryDocumentStore;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-20, FR-31, NFR-08: indeks zasilany przy wpisie i po OCR; brak wyników ze spraw bez uprawnień (D4); odbudowa. */
class SearchServiceTest extends ServiceTestSupport {

    @Test
    void indeksZasilanyPrzyWpisieIPoOcrZFiltremDostepu() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            assertThat(s.search.search("Przykładowy", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).anySatisfy(h -> {
                assertThat(h.caseId()).isEqualTo(cf.getId());
                assertThat(h.type()).isEqualTo("sprawa");
            });
            assertThat(s.search.search("przykladow", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).as("bez znaków diakrytycznych").isNotEmpty();
            assertThat(s.search.search(cf.getDocket().format(), REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).isNotEmpty();
            assertThat(s.search.search("Przykładowy", "asystent.obcy", EnumSet.of(AppRole.TK_ASYSTENT), 10)).as("asystent poza zespołem: brak wyników").isEmpty();
            assertThat(s.search.search("Przykładowy", "admin", EnumSet.of(AppRole.TK_ADMIN), 10)).isEmpty();
            assertThat(s.search.search("", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).isEmpty();
        });

        byte[] pdf = "%PDF-1.7 skan".getBytes(StandardCharsets.UTF_8);
        CaseDocument scan = tx(s -> s.documents.upload(cf.getId(), "Odpis wyroku Sądu Okręgowego", DocumentKind.ZALACZNIK, pdf, "application/pdf", 2, "k"));
        CaseDocument secret = tx(s -> s.documents.upload(cf.getId(), "Załącznik niejawny o kodeksie", DocumentKind.NIEJAWNY, pdf, "application/pdf", 1, "k"));
        run(s -> {
            assertThat(s.search.search("wyroku", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).anySatisfy(h -> assertThat(h.documentId()).isEqualTo(scan.getId()));
            assertThat(s.search.search("niejawny", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).as("dokumenty niejawne nie są indeksowane").isEmpty();
            String ocr = "Wyrok w imieniu Rzeczypospolitej Polskiej. Sąd Okręgowy oddala apelację powoda w sprawie o zapłatę.";
            s.documents.applyProcessing(scan.getId(), new CaseDocumentService.ProcessingResult(pdf, InMemoryDocumentStore.sha256(pdf), true, true, "{}", false, true, ocr), "worker:w1");
            List<SearchIndex.Hit> hits = s.search.search("apelację", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10);
            assertThat(hits).singleElement().satisfies(h -> {
                assertThat(h.documentId()).isEqualTo(scan.getId());
                assertThat(h.fragment()).contains("apelację");
                assertThat(h.pages()).isEqualTo("k. 15–16");
            });
            int before = searchIndex.size();
            assertThat(s.search.rebuild()).isGreaterThanOrEqualTo(2);
            assertThat(searchIndex.size()).isLessThanOrEqualTo(before);
            assertThat(s.search.search("Przykładowy", REFERENT, EnumSet.of(AppRole.TK_REFERENT), 10)).isNotEmpty();
        });
        assertThat(InMemorySearchIndex.norm("Żółć ŁÓDŹ")).isEqualTo("zolc lodz");
        assertThat(secret.getStorageKey()).isNull();
    }
}
