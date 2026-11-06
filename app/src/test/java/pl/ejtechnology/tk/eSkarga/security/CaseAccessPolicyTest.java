package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy.Delegation;

/** D4, BR-10, FR-24, FR-36 (T-016). */
class CaseAccessPolicyTest {

    private final CaseAssignment ts312 = new CaseAssignment("pfikcyjny", Set.of("tprobny"), Set.of("mprzykladowski"));

    @Test
    void assistantSeesNotesOnlyInOwnJudgesCases() {
        assertThat(CaseAccessPolicy.canReadInternalNotes("tprobny", Set.of(AppRole.TK_ASYSTENT), ts312)).isTrue();
        CaseAssignment otherJudge = new CaseAssignment("ewzorcowa", Set.of(), Set.of("mprzykladowski"));
        assertThat(CaseAccessPolicy.canReadInternalNotes("tprobny", Set.of(AppRole.TK_ASYSTENT), otherJudge)).isFalse();
        assertThat(CaseAccessPolicy.canReadCase("tprobny", Set.of(AppRole.TK_ASYSTENT), otherJudge)).isFalse();
    }

    @Test
    void clerkReadsPublicRecordsButNeverInternalNotes() {
        assertThat(CaseAccessPolicy.canReadCase("mprzykladowski", Set.of(AppRole.TK_REFERENT), ts312)).isTrue();
        assertThat(CaseAccessPolicy.canReadInternalNotes("mprzykladowski", Set.of(AppRole.TK_REFERENT), ts312)).isFalse();
    }

    @Test
    void headOfDivisionSeesNoInternalNotes() {
        assertThat(CaseAccessPolicy.canReadInternalNotes("eszablon", Set.of(AppRole.TK_KIEROWNIK), ts312)).isFalse();
    }

    @Test
    void technicalAdministratorDoesNotReadCaseFiles() {
        assertThat(CaseAccessPolicy.canReadCase("aadmin", Set.of(AppRole.TK_ADMIN), ts312)).isFalse();
        // konto z rolą administratora i rolami merytorycznymi z ID-users czyta akta według tych ról (nie odmowa dla wszystkich)
        assertThat(CaseAccessPolicy.canReadCase("admin.wszystko", Set.of(AppRole.TK_ADMIN, AppRole.TK_REFERENT), ts312)).isTrue();
        assertThat(CaseAccessPolicy.canReadCase("admin.wszystko", Set.of(AppRole.TK_ADMIN, AppRole.TK_ASYSTENT), ts312)).isFalse();
        assertThat(CaseAccessPolicy.canReadCase("asia", Set.of(AppRole.TK_ADMIN, AppRole.TK_ASYSTENT),
                new CaseAccessPolicy.CaseAssignment("sedzia1", Set.of("asia"), Set.of()))).isTrue();
    }

    @Test
    void actingAsPresidentRequiresValidDelegationOnTheDay() {
        Delegation d = new Delegation("mmakietowa", CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 31), false);
        Set<AppRole> roles = Set.of(AppRole.TK_SEDZIA, AppRole.TK_PREZES);
        assertThat(CaseAccessPolicy.canActAsPresident("mmakietowa", roles, Set.of(d), LocalDate.of(2026, 11, 3))).isTrue();
        assertThat(CaseAccessPolicy.canActAsPresident("mmakietowa", roles, Set.of(d), LocalDate.of(2027, 1, 1))).isFalse();
        assertThat(CaseAccessPolicy.canActAsPresident("mmakietowa", roles, Set.of(d), LocalDate.of(2026, 10, 29))).isFalse();
        assertThat(CaseAccessPolicy.canActAsPresident("ewzorcowa", roles, Set.of(d), LocalDate.of(2026, 11, 3))).isFalse();
    }

    @Test
    void revokedDelegationDoesNotAuthorise() {
        Delegation revoked = new Delegation("eszablon", CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 30), true);
        assertThat(CaseAccessPolicy.canActAsPresident("eszablon", Set.of(AppRole.TK_SEDZIA), Set.of(revoked), LocalDate.of(2026, 9, 20))).isFalse();
    }
}
