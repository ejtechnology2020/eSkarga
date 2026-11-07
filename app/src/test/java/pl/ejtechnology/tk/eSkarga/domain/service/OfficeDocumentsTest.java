package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.documents.FileTypes;
import pl.ejtechnology.tk.eSkarga.documents.FileTypesTest;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.support.InMemoryDocumentStore;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/**
 * ADR 0026: DOCX z rejestracji wpływu trafia do akt jako oryginał czekający na konwersję; wynik workera (PDF/A z usługi dokumentów)
 * staje się kartą akt, a oryginał zostaje w repozytorium pod osobnym kluczem.
 */
class OfficeDocumentsTest extends ServiceTestSupport {

    @Test
    void docxZWplywuPrzezKonwersjeDoKartyAkt() throws IOException {
        byte[] docx = FileTypesTest.docx("Skarga konstytucyjna");
        long letterId = tx(s -> registerLetter(s)).getId();
        var scan = tx(s -> s.incoming.attachScan(letterId, "skarga.docx", docx, REFERENT, null));
        assertThat(scan.getContentType()).isEqualTo(FileTypes.DOCX);

        CaseFile cf = tx(s -> createCase(s, letterId));
        CaseDocument pismo = tx(s -> s.cases.documents(s.cases.require(cf.getId())).get(0));
        assertThat(pismo.getSourceContentType()).isEqualTo(FileTypes.DOCX);
        assertThat(pismo.getSourceFileName()).isEqualTo("skarga.docx");
        assertThat(pismo.isAwaitingConversion()).isTrue();
        assertThat(pismo.isPdf()).isFalse();
        assertThat(pismo.getStorageKey()).endsWith(".docx");
        assertThat(store.files().get(pismo.getStorageKey()).bytes()).isEqualTo(docx);
        run(s -> assertThat(s.documents.pendingProcessing()).extracting(CaseDocument::getId).contains(pismo.getId()));

        byte[] pdf = "%PDF-1.7\n%PDF/A z konwersji\n".getBytes(StandardCharsets.ISO_8859_1);
        String originalKey = pismo.getStorageKey();
        tx(s -> s.documents.applyProcessing(pismo.getId(), new CaseDocumentService.ProcessingResult(pdf, InMemoryDocumentStore.sha256(pdf), true, false,
                "{\"podpisany\":false}", false, true, "Skarga konstytucyjna", "docx"), "worker:test"));
        run(s -> {
            CaseDocument d = s.documents.require(pismo.getId());
            assertThat(d.isPdf()).isTrue();
            assertThat(d.isAwaitingConversion()).isFalse();
            assertThat(d.getStorageKey()).endsWith("-v2.pdf");
            assertThat(d.getSourceStorageKey()).as("oryginał DOCX zostaje w repozytorium").isEqualTo(originalKey);
            assertThat(store.files().get(d.getStorageKey()).bytes()).isEqualTo(pdf);
            assertThat(store.files().get(originalKey).bytes()).isEqualTo(docx);
            assertThat(d.isPdfaCompliant()).isTrue();
            assertThat(s.documents.pendingProcessing()).extracting(CaseDocument::getId).doesNotContain(d.getId());
            assertThat(s.audit.forCase(cf.getDocket().format(), 50)).anySatisfy(e -> assertThat(e.getAction()).contains("z DOCX"));
        });
    }

    @Test
    void uploadPrzyjmujeDocxIOdrzucaInnePliki() throws IOException {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        byte[] docx = FileTypesTest.docx("Pismo uzupełniające");
        CaseDocument d = tx(s -> s.documents.upload(cf.getId(), "Pismo uzupełniające.docx", pl.ejtechnology.tk.eSkarga.domain.DocumentKind.ZALACZNIK, docx, FileTypes.DOCX, 1, REFERENT));
        assertThat(d.isAwaitingConversion()).isTrue();
        assertThat(d.getStorageKey()).endsWith(".docx");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx(s -> s.documents.upload(cf.getId(), "x", pl.ejtechnology.tk.eSkarga.domain.DocumentKind.ZALACZNIK,
                "<html/>".getBytes(StandardCharsets.UTF_8), "text/html", 1, REFERENT))).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(FileTypes.PDF, FileTypes.DOCX, FileTypes.DOC)).allMatch(FileTypes::isCaseDocument);
    }
}
