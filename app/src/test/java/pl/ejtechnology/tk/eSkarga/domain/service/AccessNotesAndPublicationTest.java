package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.AccessRequest;
import pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Publication;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-15, UC-16, FR-22, FR-24, BR-09, D4: wgląd, notatki zespołu, publikacja (T-035, T-036). */
class AccessNotesAndPublicationTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.fikcyjny";
    private static final String ASYSTENT = "asystent.testowy";
    private static final String OBCY = "referent.obcy";

    private CaseFile caseWithTeam() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            s.em.persist(new CaseAssignment(loaded, SEDZIA, AssignmentRole.JUDGE, TODAY));
            s.em.persist(new CaseAssignment(loaded, ASYSTENT, AssignmentRole.TEAM, TODAY));
            CaseDocument skarga = s.cases.documents(loaded).get(0);
            skarga.attachFile("akta/ts-1-26/0001.pdf", "a".repeat(64));
            CaseDocument niejawny = new CaseDocument(loaded, "Załącznik z klauzulą", DocumentKind.NIEJAWNY, TODAY, REFERENT);
            niejawny.paginate(1, 15, 15);
            s.em.persist(niejawny);
            CaseDocument notatka = new CaseDocument(loaded, "Notatka asystenta", DocumentKind.NOTATKA, TODAY, ASYSTENT);
            notatka.paginate(1, 16, 16);
            notatka.attachFile("akta/ts-1-26/notatka.pdf", "b".repeat(64));
            s.em.persist(notatka);
        });
        return cf;
    }

    @Test
    void notatkiWewnetrzneTylkoDlaZespoluSedziego() {
        CaseFile cf = caseWithTeam();
        run(s -> s.notes.addNote(cf.getId(), "Uwagi do skargi", "Brak wskazania wzorca.", ASYSTENT, Set.of(AppRole.TK_ASYSTENT)));
        long denials = auditCount("odmowa: notatki wewnętrzne");
        assertThatThrownBy(() -> run(s -> s.notes.addNote(cf.getId(), "x", "y", OBCY, Set.of(AppRole.TK_REFERENT)))).isInstanceOf(SecurityException.class);
        assertThat(auditCount("odmowa: notatki wewnętrzne")).isEqualTo(denials + 1);
        run(s -> {
            assertThat(s.notes.notes(cf.getId(), SEDZIA, Set.of(AppRole.TK_SEDZIA))).hasSize(1);
            assertThat(s.notes.notes(cf.getId(), REFERENT, Set.of(AppRole.TK_REFERENT))).as("referent sprawy nie widzi notatek zespołu").isEmpty();
            CaseDocument skarga = s.cases.documents(s.cases.require(cf.getId())).get(0);
            assertThat(NoteService.citation(s.cases.require(cf.getId()), skarga, 3)).isEqualTo(cf.getDocket().format() + ", k. 3");
        });
    }

    @Test
    void dostepDoSprawyWedlugPrzydzialuZOdmowaWAudycie() {
        CaseFile cf = caseWithTeam();
        long denials = auditCount("odmowa dostępu do sprawy");
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(s.caseAccess.canRead(loaded, REFERENT, Set.of(AppRole.TK_REFERENT))).as("referent przypisany przy założeniu").isTrue();
            assertThat(s.caseAccess.canRead(loaded, SEDZIA, Set.of(AppRole.TK_SEDZIA))).isTrue();
            assertThat(s.caseAccess.canRead(loaded, OBCY, Set.of(AppRole.TK_REFERENT))).as("referent czyta akta jawne wydziału (§9.2)").isTrue();
            assertThat(s.caseAccess.canRead(loaded, "asystent.obcy", Set.of(AppRole.TK_ASYSTENT))).as("asystent poza zespołem").isFalse();
            assertThat(s.caseAccess.canRead(loaded, "admin", Set.of(AppRole.TK_ADMIN))).as("administrator techniczny nie czyta akt").isFalse();
            assertThat(s.caseAccess.activeAssignments()).extracting(CaseAssignment::getUserId).contains(REFERENT, SEDZIA, ASYSTENT);
        });
        assertThat(auditCount("odmowa dostępu do sprawy")).isEqualTo(denials + 2);
    }

    @Test
    void osobaTrzeciaWidziTylkoWersjeZanonimizowaneINigdyDokumentowNiejawnych() {
        CaseFile cf = caseWithTeam();
        AccessRequest participant = tx(s -> s.access.register(cf.getId(), "adw. Anna Testowa", true, "całość akt", BigDecimal.ZERO, REFERENT));
        AccessRequest third = tx(s -> s.access.register(cf.getId(), "red. Tomasz Przykładowy", false, "skarga", new BigDecimal("12.00"), REFERENT));
        run(s -> {
            assertThat(s.access.visibleFileKeys(s.em.find(AccessRequest.class, participant.getId()))).containsExactly("akta/ts-1-26/0001.pdf");
            assertThat(s.access.visibleFileKeys(s.em.find(AccessRequest.class, third.getId()))).isEmpty();
        });
        assertThatThrownBy(() -> run(s -> s.access.schedule(third.getId(), Instant.parse("2026-11-10T09:00:00Z"), REFERENT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BR-09");
        run(s -> {
            s.cases.documents(s.cases.require(cf.getId())).get(0).setAnonymisedKey("akta/ts-1-26/0001-anon.pdf");
            s.access.anonymisedReady(third.getId(), REFERENT);
            s.access.schedule(third.getId(), Instant.parse("2026-11-10T09:00:00Z"), REFERENT);
        });
        run(s -> assertThat(s.access.visibleFileKeys(s.em.find(AccessRequest.class, third.getId()))).containsExactly("akta/ts-1-26/0001-anon.pdf"));
    }

    @Test
    void publikacjaDokumentuZArt80WymagaAnonimizacji() {
        CaseFile cf = caseWithTeam();
        run(s -> s.cases.require(cf.getId()).current().setFlagAnonymise(true));
        long docId = tx(s -> s.cases.documents(s.cases.require(cf.getId())).get(0).getId());
        Publication p = tx(s -> s.access.queueForPublication(docId, REFERENT));
        run(s -> assertThat(s.access.pendingPublications()).extracting(Publication::getId).contains(p.getId()));
        assertThatThrownBy(() -> run(s -> s.access.approvePublication(p.getId(), REFERENT))).isInstanceOf(IllegalStateException.class).hasMessageContaining("art. 80");
        run(s -> s.em.find(Publication.class, p.getId()).anonymised("pub/ts-1-26-anon.pdf", 7));
        run(s -> s.access.approvePublication(p.getId(), REFERENT));
        run(s -> assertThat(s.access.pendingPublications()).extracting(Publication::getId).doesNotContain(p.getId()));
    }
}
