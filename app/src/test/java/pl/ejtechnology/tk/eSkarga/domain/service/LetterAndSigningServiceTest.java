package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Delivery;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.domain.model.SignatureStatus;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-07, UC-10, D10, D13 (T-026, T-030, T-031, T-040): projekt pisma z szablonu, podpis dwuetapowy z ruchem procesu, skany i przetwarzanie. */
class LetterAndSigningServiceTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.fikcyjny";

    private CaseFile caseInReview() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES));
            s.templates.seedDefaults(docs.defaultTemplates(), "admin");
        });
        return cf;
    }

    private Task task(CaseFile cf) {
        return engine.getTaskService().createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult();
    }

    @Test
    void projektZarzadzeniaOBrakachIPodpisDwuetapowy() {
        CaseFile cf = caseInReview();
        assertThat(task(cf).getTaskDefinitionKey()).isEqualTo("T_Kontrola");

        ProceduralAct act = tx(s -> s.letters.draft(cf.getId(), LetterKind.ZARZ_BRAKI, Map.of("braki", List.of("pełnomocnictwo szczególne")), "Piotr Fikcyjny", SEDZIA));
        assertThat(act.getStatus()).isEqualTo(ActStatus.PROJEKT);
        assertThat(act.getSubtype()).isEqualTo("BRAKI_FORMALNE");
        assertThat(docs.rendered).contains("ZARZ_BRAKI");
        long docId = act.getDocument().getId();
        run(s -> {
            CaseDocument d = s.documents.require(docId);
            assertThat(d.getKind()).isEqualTo(DocumentKind.CZYNNOSC);
            assertThat(d.getTemplateVersion().getCode()).isEqualTo("ZARZ_BRAKI");
            assertThat(d.getPageFrom()).as("po skardze (k. 1–14) i zarządzeniu o wyznaczeniu sędziego (k. 15)").isEqualTo(16);
            assertThat(store.verifySha256(d.getStorageKey(), d.getSha256())).isTrue();
            assertThat(new String(store.files().get(d.getStorageKey()).bytes(), StandardCharsets.UTF_8)).contains(cf.getDocket().format()).contains("Jan Przykładowy").contains("pełnomocnictwo");
        });
        assertThat(task(cf).getTaskDefinitionKey()).as("projekt rozstrzyga wynik kontroli → proces czeka na podpis zarządzenia").isEqualTo("T_Braki");

        var d = tx(s -> s.signing.begin(docId, List.of("MIIB..."), "RSA_SHA256", SEDZIA));
        assertThat(d.sessionId()).startsWith("sesja-");
        assertThat(d.digestBase64()).isNotBlank();

        ProceduralAct signed = tx(s -> s.signing.complete(docId, d.sessionId(), "cG9kcGlz", true, LetterKind.ZARZ_BRAKI, SEDZIA));
        assertThat(signed.getStatus()).isEqualTo(ActStatus.PODPISANA);
        run(s -> {
            CaseDocument doc = s.documents.require(docId);
            assertThat(doc.getVersion()).isEqualTo(2);
            assertThat(doc.getSignatureStatus()).isEqualTo(SignatureStatus.KWALIFIKOWANY_WAZNY);
            assertThat(store.files().get(doc.getStorageKey()).locked()).as("podpisana czynność z Object Lock").isTrue();
            assertThat(s.cases.require(cf.getId()).current().getStatus()).isEqualTo(CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW);
            assertThat(s.deliveries.toSend()).extracting(Delivery::getDocument).extracting(CaseDocument::getId).contains(docId);
        });
        // ruch procesu w tej samej transakcji: T_Braki zakończone → external task doręczenia → (po wysyłce) bramka z timerem wstępnym
        var locked = engine.getExternalTaskService().fetchAndLock(1, "test").topic("doreczenie-pisma", 10_000).execute();
        assertThat(locked).hasSize(1);
        engine.getExternalTaskService().complete(locked.get(0).getId(), "test");
        Job timer = engine.getManagementService().createJobQuery().processInstanceId(locked.get(0).getProcessInstanceId()).timers().singleResult();
        assertThat(timer.getDuedate()).isEqualTo(date(TODAY.plusDays(SigningService.PROVISIONAL_WAIT_DAYS + 1)));
        assertThatThrownBy(() -> tx(s -> s.signing.begin(docId, List.of("x"), "RSA_SHA256", SEDZIA))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void odmowaZPodstawaIPodpisSkladuWieluSedziow() {
        CaseFile cf = caseInReview();
        ProceduralAct act = tx(s -> s.letters.draft(cf.getId(), LetterKind.POST_ODMOWA, Map.of("podstawaOdmowy", 2, "uzasadnienie", List.of("Skarga jest oczywiście bezzasadna.")), "Piotr Fikcyjny", SEDZIA));
        assertThat(act.getSubtype()).isEqualTo("ODMOWA_PKT_2");
        assertThat(act.getLegalBasis()).isEqualTo("art. 61 ust. 4 pkt 2");
        assertThat(task(cf).getTaskDefinitionKey()).isEqualTo("T_Odmowa");
        long docId = act.getDocument().getId();
        var d1 = tx(s -> s.signing.begin(docId, List.of("c1"), "RSA_SHA256", SEDZIA));
        tx(s -> s.signing.complete(docId, d1.sessionId(), "p1", false, LetterKind.POST_ODMOWA, SEDZIA));
        run(s -> {
            assertThat(s.documents.require(docId).getSignatureStatus()).isEqualTo(SignatureStatus.CZESCIOWY);
            assertThat(s.cases.require(cf.getId()).current().getStatus()).as("status zmienia dopiero ostatni podpis").isEqualTo(CaseStatus.WE_WSTEPNEJ_KONTROLI);
        });
        assertThat(task(cf).getTaskDefinitionKey()).isEqualTo("T_Odmowa");
        tx(s -> s.signing.complete(docId, "sesja-2", "p2", true, LetterKind.POST_ODMOWA, "sedzia.drugi"));
        run(s -> assertThat(s.cases.require(cf.getId()).current().getStatus()).isEqualTo(CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE));
        assertThat(engine.getExternalTaskService().fetchAndLock(1, "t2").topic("doreczenie-pisma", 1000).execute()).hasSize(1);
    }

    @Test
    void nieudanaWalidacjaPodpisuWycofujeCalaTransakcje() {
        CaseFile cf = caseInReview();
        ProceduralAct act = tx(s -> s.letters.draft(cf.getId(), LetterKind.POST_NADANIE_BIEGU, Map.of("uzasadnienie", List.of("Skarga spełnia wymogi.")), "Piotr Fikcyjny", SEDZIA));
        long docId = act.getDocument().getId();
        docs.signaturesValid = false;
        try {
            assertThatThrownBy(() -> tx(s -> s.signing.complete(docId, "sesja-x", "zly", true, LetterKind.POST_NADANIE_BIEGU, SEDZIA)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("DSS");
        } finally {
            docs.signaturesValid = true;
        }
        run(s -> {
            assertThat(s.documents.require(docId).getVersion()).isEqualTo(1);
            assertThat(s.signing.actFor(s.documents.require(docId)).orElseThrow().getStatus()).isEqualTo(ActStatus.PROJEKT);
        });
        assertThat(task(cf).getTaskDefinitionKey()).isEqualTo("T_Nadanie");
        tx(s -> s.signing.complete(docId, "sesja-ok", "dobry", true, LetterKind.POST_NADANIE_BIEGU, SEDZIA));
        assertThat(task(cf)).as("po postanowieniu o nadaniu biegu proces czeka na przekazanie (external task)").isNull();
        assertThat(engine.getExternalTaskService().fetchAndLock(1, "t3").topic("przekazanie-merytoryczne", 1000).execute()).hasSize(1);
    }

    @Test
    void skanDoAktIPrzetworzeniePrzezWorkera() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        byte[] pdf = "%PDF-1.7 skan skargi".getBytes(StandardCharsets.UTF_8);
        CaseDocument scan = tx(s -> s.documents.upload(cf.getId(), "Skarga konstytucyjna – skan", DocumentKind.ZALACZNIK, pdf, "application/pdf", 3, "kprzykladowy"));
        assertThat(scan.getPageFrom()).isEqualTo(15);
        assertThat(scan.getPageTo()).isEqualTo(17);
        assertThatThrownBy(() -> tx(s -> s.documents.upload(cf.getId(), "x", DocumentKind.ZALACZNIK, "MZ exe".getBytes(), "application/pdf", 1, "k")))
                .isInstanceOf(IllegalArgumentException.class);
        CaseDocument secret = tx(s -> s.documents.upload(cf.getId(), "Załącznik z klauzulą", DocumentKind.NIEJAWNY, pdf, "application/pdf", 1, "k"));
        assertThat(secret.getStorageKey()).as("dokument niejawny: tylko metadane").isNull();

        run(s -> assertThat(s.documents.pendingProcessing()).extracting(CaseDocument::getId).contains(scan.getId()).doesNotContain(secret.getId()));
        byte[] ocr = "%PDF-1.7 skan skargi + OCR".getBytes(StandardCharsets.UTF_8);
        var result = new CaseDocumentService.ProcessingResult(ocr, pl.ejtechnology.tk.eSkarga.support.InMemoryDocumentStore.sha256(ocr), true, true, "{\"podpisany\":false}", false, true);
        run(s -> s.documents.applyProcessing(scan.getId(), result, "worker:w1"));
        run(s -> {
            CaseDocument d = s.documents.require(scan.getId());
            assertThat(d.getVersion()).isEqualTo(2);
            assertThat(d.isOcrDone()).isTrue();
            assertThat(d.isPdfaCompliant()).isTrue();
            assertThat(d.getSignatureStatus()).isEqualTo(SignatureStatus.BRAK);
            assertThat(s.documents.pendingProcessing()).extracting(CaseDocument::getId).doesNotContain(scan.getId());
            s.documents.applyProcessing(scan.getId(), result, "worker:w1");
            assertThat(s.documents.require(scan.getId()).getVersion()).as("ponowiony wynik jest neutralny").isEqualTo(2);
        });
    }
}
