package pl.ejtechnology.tk.eSkarga.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.support.JpaTestSupport;

/**
 * T-010: migracje Flyway V1–V3 + walidacja mapowania encji przez Hibernate (ADR 0011) oraz wersjonowanie wpisu zamiast UPDATE.
 */
class ModelSchemaTest {

    static JpaTestSupport db;

    @BeforeAll
    static void start() {
        db = new JpaTestSupport();
    }

    @AfterAll
    static void stop() {
        db.close();
    }

    @Test
    void schemaMatchesEntitiesAndRoundTripsCaseFile() {
        Long caseId = db.tx(em -> {
            CaseFile cf = new CaseFile(DocketNumber.parse("Ts 312/26"), LocalDate.of(2026, 10, 14), "mprzykladowski");
            CaseFileVersion v1 = cf.addVersion(new CaseFileVersion(cf, 1, CaseStatus.ZAREJESTROWANA, "mprzykladowski"));
            v1.setReviewSubject("art. 505¹ § 1 k.p.c.");
            em.persist(cf);
            Party skarzacy = new Party(cf, PartyRole.SKARZACY, "Jan Przykładowy", "ul. Przykładowa 1, 00-001 Przykładowo", null);
            Party pelnomocnik = new Party(cf, PartyRole.PELNOMOCNIK, "adw. Anna Testowa", null, "AE:PL-12345-67890-ABCDE-12");
            em.persist(skarzacy);
            em.persist(pelnomocnik);
            em.persist(new PowerOfAttorney(pelnomocnik, PowerOfAttorneyKind.OGOLNE, Profession.ADWOKAT, "WAW/Adw/1234", LocalDate.of(2026, 10, 1), null));
            em.persist(new FinalRuling(cf, "Sąd Okręgowy w Przykładowie", "II Ca 123/26", LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 14), false));
            em.persist(new ReviewSubject(cf, "k.p.c.", "art. 505¹ § 1", "art. 45 ust. 1, art. 78 Konstytucji"));
            CaseDocument skarga = new CaseDocument(cf, "Skarga konstytucyjna", DocumentKind.PISMO_PROCESOWE, LocalDate.of(2026, 10, 14), "kwzorek");
            skarga.paginate(1, 1, 14);
            skarga.attachFile("akta/ts-312-26/k0001.pdf", "9f3c".repeat(16));
            em.persist(skarga);
            IncomingLetter dw = new IncomingLetter("DW 2026/04801", Instant.parse("2026-10-14T08:02:00Z"), LocalDate.of(2026, 10, 13), false,
                    IntakeChannel.POCZTA, "adw. Anna Testowa", 2, 2, false, "kwzorek");
            dw.qualify(LetterQualification.NOWA_SKARGA_TS, cf);
            em.persist(dw);
            em.persist(new CaseAssignment(cf, "pfikcyjny", AssignmentRole.JUDGE, LocalDate.of(2026, 10, 20)));
            em.persist(new InternalNote(cf, "tprobny", "Notatka do Ts 312/26", "Brak pełnomocnictwa szczególnego."));
            em.persist(new Highlight(skarga, "tprobny", 1, "{\"x\":0.1,\"y\":0.2,\"w\":0.5,\"h\":0.02}", "sprawdzić datę"));
            em.persist(new Delegation("mmakietowa", "PREZES_TK", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 31), "zarządzenie nr 12/2026", "aadmin"));
            em.persist(new Holiday(LocalDate.of(2026, 11, 10), "Dzień wolny za 15.08.2026", "aadmin"));
            em.persist(new Court("SO-PRZ", "Sąd Okręgowy w Przykładowie"));
            em.persist(new ReviewStandard("K-045-1", "art. 45 ust. 1 Konstytucji", "prawo do sądu"));
            em.persist(new LetterTemplate("ZARZ_BRAKI", "Zarządzenie o usunięciu braków", 1, "<html/>", "aadmin"));
            return cf.getId();
        });

        db.run(em -> {
            CaseFile cf = em.find(CaseFile.class, caseId);
            assertThat(cf.getDocket().format()).isEqualTo("Ts 312/26");
            assertThat(cf.current().getVersion()).isEqualTo(1);
            // korekta (UC-22): nowa wersja, bez UPDATE poprzedniej
            CaseFileVersion v2 = cf.current().next(CaseStatus.ZAREJESTROWANA, "mprzykladowski");
            v2.setReviewSubject("art. 505¹ § 1 i art. 505⁸ k.p.c.");
            v2.setJustification("Skarga wskazuje także art. 505⁸ (k. 3).");
            v2.setApprovedBy("eszablon");
            em.persist(cf.addVersion(v2));
        });

        db.run(em -> {
            CaseFile cf = em.find(CaseFile.class, caseId);
            assertThat(cf.getVersions()).hasSize(2);
            assertThat(cf.current().getVersion()).isEqualTo(2);
            assertThat(cf.current().getReviewSubject()).contains("505⁸");
            CaseFileVersion v1 = cf.getVersions().stream().filter(v -> v.getVersion() == 1).findFirst().orElseThrow();
            assertThat(v1.getReviewSubject()).isEqualTo("art. 505¹ § 1 k.p.c.");
            List<Party> parties = em.createQuery("select p from Party p where p.caseFile = :c order by p.id", Party.class).setParameter("c", cf).getResultList();
            assertThat(parties).extracting(Party::getDisplayName).containsExactly("Jan Przykładowy", "adw. Anna Testowa");
            assertThat(parties.get(1).initials()).isEqualTo("A.T.");
            assertThat(em.find(ArchiveCategory.class, ClosingMode.ODMOWA_PRAWOMOCNA).getCategory()).isEqualTo("B10");
        });
    }

    @Test
    void classifiedDocumentNeverGetsAFile() {
        db.run(em -> {
            CaseFile cf = new CaseFile(DocketNumber.parse("Ts 1/26"), LocalDate.of(2026, 1, 5), "x");
            cf.addVersion(new CaseFileVersion(cf, 1, CaseStatus.ZAREJESTROWANA, "x"));
            em.persist(cf);
            CaseDocument secret = new CaseDocument(cf, "Załącznik z klauzulą", DocumentKind.NIEJAWNY, LocalDate.of(2026, 1, 5), "x");
            assertThatThrownBy(() -> secret.attachFile("k", "h")).isInstanceOf(IllegalStateException.class);
            em.persist(secret);
            assertThat(secret.isClassified()).isTrue();
            assertThat(secret.getStorageKey()).isNull();
        });
    }

    @Test
    void templateActivationNeedsSecondPerson() {
        LetterTemplate t = new LetterTemplate("X", "x", 1, "<html/>", "aadmin");
        assertThatThrownBy(() -> t.activate("aadmin")).isInstanceOf(IllegalArgumentException.class);
        t.activate("eszablon");
        assertThat(t.getStatus()).isEqualTo(TemplateStatus.AKTYWNA);
    }
}
