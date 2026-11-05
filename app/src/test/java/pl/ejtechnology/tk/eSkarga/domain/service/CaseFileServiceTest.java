package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;
import pl.ejtechnology.tk.eSkarga.support.Wiring;

/** UC-03, UC-04, D1, BR-01, NFR-01: założenie sprawy w jednej transakcji ze startem procesu (T-013). */
class CaseFileServiceTest extends ServiceTestSupport {

    @Test
    void zalozenieSprawyNadajeSygnatureWpisujeWersje1IStartujeProces() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));

        assertThat(cf.getDocket().symbol()).isEqualTo(DocketSymbol.TS);
        assertThat(cf.getDocket().year()).isEqualTo(2026);
        assertThat(cf.getDocket().format()).endsWith("/26");
        assertThat(cf.current().getVersion()).isEqualTo(1);
        assertThat(cf.current().getStatus()).isEqualTo(CaseStatus.ZAREJESTROWANA);
        assertThat(cf.getProcessInstanceId()).isNotBlank();

        run(s -> {
            IncomingLetter l = s.em.find(IncomingLetter.class, letterId);
            assertThat(l.getQualification()).isEqualTo(LetterQualification.NOWA_SKARGA_TS);
            CaseFile loaded = s.cases.byDocket(cf.getDocket()).orElseThrow();
            assertThat(s.cases.parties(loaded)).hasSize(2);
            assertThat(s.cases.documents(loaded)).singleElement().satisfies(d -> {
                assertThat(d.getPageFrom()).isEqualTo(1);
                assertThat(d.getPageTo()).isEqualTo(14);
            });
        });

        ProcessInstance pi = engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult();
        assertThat(pi).isNotNull();
        Task waiting = engine.getTaskService().createTaskQuery().processInstanceId(pi.getId()).singleResult();
        assertThat(waiting.getTaskDefinitionKey()).isEqualTo("T_Wyznacz");
        assertThat(engine.getRuntimeService().getVariable(pi.getId(), ProcessVariables.SYGNATURA)).isEqualTo(cf.getDocket().format());
    }

    @Test
    void bladStartuProcesuWycofujeWpisINieZuzywaNumeru() {
        long letterId = tx(s -> registerLetter(s)).getId();
        DocketNumber expected = tx(s -> s.docketNumbers.peekNextNumber(DocketSymbol.TS, TODAY));

        assertThatThrownBy(() -> db.tx(em -> {
            PreliminaryReviewProcess broken = new PreliminaryReviewProcess() {
                @Override
                public ProcessInstance start(long caseId, DocketNumber sygnatura, ProcessVariables.RodzajPisma rodzaj) {
                    throw new IllegalStateException("silnik niedostępny");
                }
            };
            Services s = new Services(em, broken);
            Wiring.inject(s.cases, broken);
            return createCase(s, letterId);
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("silnik");

        run(s -> {
            assertThat(s.em.find(IncomingLetter.class, letterId).getQualification()).isNull();
            assertThat(s.cases.byDocket(expected)).isEmpty();
            assertThat(s.docketNumbers.peekNextNumber(DocketSymbol.TS, TODAY)).isEqualTo(expected);
        });
        CaseFile cf = tx(s -> createCase(s, letterId));
        assertThat(cf.getDocket()).isEqualTo(expected);
    }

    @Test
    void pismoDoSprawyDostajeKolejneKartyIWysylaKomunikatDoProcesu() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        long second = tx(s -> registerLetter(s)).getId();

        CaseDocument doc = tx(s -> s.cases.attachLetter(cf.getId(), second, "Pismo uzupełniające", 3, false, REFERENT));
        assertThat(doc.getPageFrom()).isEqualTo(15);
        assertThat(doc.getPageTo()).isEqualTo(17);
        // proces czeka na wyznaczenie sędziego, więc komunikat o uzupełnieniu braków nie ma odbiorcy – pismo i tak trafia do akt
        tx(s -> assertThat(s.em.find(IncomingLetter.class, second).getQualification()).isEqualTo(LetterQualification.PISMO_DO_SPRAWY));
    }

    @Test
    void zmianaStatusuTworzyNowaWersjeIZachowujePoprzednie() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> s.cases.changeStatus(cf.getId(), CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO, REFERENT));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.getVersions()).hasSize(2);
            assertThat(loaded.getVersions().get(0).getStatus()).isEqualTo(CaseStatus.ZAREJESTROWANA);
            assertThat(loaded.current().getVersion()).isEqualTo(2);
            assertThat(loaded.current().getStatus()).isEqualTo(CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO);
        });
    }

    @Test
    void sprawaZakonczonaJestTylkoDoOdczytu() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> s.cases.changeStatus(cf.getId(), CaseStatus.ZAKONCZONA_NADANO_BIEG, REFERENT));
        long second = tx(s -> registerLetter(s)).getId();
        assertThatThrownBy(() -> tx(s -> s.cases.attachLetter(cf.getId(), second, "Pismo", 1, false, REFERENT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BR-08");
    }

    @Test
    void peekNieZuzywaNumeru() {
        DocketNumber a = tx(s -> s.docketNumbers.peekNextNumber(DocketSymbol.TW, LocalDate.of(2026, 11, 3)));
        DocketNumber b = tx(s -> s.docketNumbers.peekNextNumber(DocketSymbol.TW, LocalDate.of(2026, 11, 3)));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void skanyWplywuStajaSieDokumentamiAkt() {
        IncomingLetter l = tx(s -> registerLetter(s));
        byte[] pdf1 = "%PDF-1.7 pismo".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] pdf2 = "%PDF-1.7 pelnomocnictwo".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        tx(s -> s.incoming.attachScan(l.getId(), "pismo.pdf", pdf1, "kprzykladowy", null));
        tx(s -> s.incoming.attachScan(l.getId(), "koperta.tif", new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0}, "kprzykladowy", null));
        tx(s -> s.incoming.attachScan(l.getId(), "pelnomocnictwo.pdf", pdf2, "kprzykladowy", null));
        CaseFile cf = tx(s -> createCase(s, l.getId()));
        java.util.List<CaseDocument> docs = tx(s -> s.cases.documents(s.em.find(CaseFile.class, cf.getId())));
        assertThat(docs).hasSize(2);
        CaseDocument pismo = docs.get(0);
        assertThat(pismo.getStorageKey()).startsWith("akta/" + cf.getId() + "/").endsWith(".pdf");
        assertThat(store.verifySha256(pismo.getStorageKey(), pismo.getSha256())).isTrue();
        assertThat(docs.get(1).getTitle()).isEqualTo("pelnomocnictwo.pdf");
        assertThat(docs.get(1).getStorageKey()).isNotNull();
    }
}
