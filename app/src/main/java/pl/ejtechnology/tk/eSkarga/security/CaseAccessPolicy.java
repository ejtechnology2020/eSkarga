package pl.ejtechnology.tk.eSkarga.security;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Uprawnienia na trzech poziomach (D4): rola ogólna z ID-users, przydział do sprawy (sędzia, zespół, referent)
 * w aplikacji oraz upoważnienie z datą od–do (BR-10). Każde zapytanie o sprawę albo dokument przechodzi tędy.
 *
 * <p>Klasa jest czysta (bez CDI), żeby reguły dało się testować jednostkowo; EJB-owa fasada dostarcza
 * przydziały i upoważnienia z bazy.
 */
public final class CaseAccessPolicy {

    /** Przydziały do sprawy: identyfikatory użytkowników w rolach procesowych. */
    public record CaseAssignment(String judgeId, Set<String> teamIds, Set<String> clerkIds) {
        public CaseAssignment {
            teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
            clerkIds = clerkIds == null ? Set.of() : Set.copyOf(clerkIds);
        }
    }

    /** Upoważnienie: osoba, funkcja i okres obowiązywania (włącznie). */
    public record Delegation(String userId, String function, LocalDate from, LocalDate to, boolean revoked) {
        public boolean validOn(LocalDate day) {
            return !revoked && !day.isBefore(from) && !day.isAfter(to);
        }
    }

    public static final String FUNCTION_PRESIDENT = "PREZES_TK";

    private CaseAccessPolicy() {
    }

    /** Czy użytkownik może czytać akta jawne sprawy. */
    public static boolean canReadCase(String userId, Set<AppRole> roles, CaseAssignment assignment) {
        Objects.requireNonNull(roles);
        // administrator techniczny nie czyta akt (macierz ról §9.2) – ale rola TK_ADMIN nie odbiera dostępu osobie, która ma
        // z ID-users także rolę merytoryczną (np. referent + administrator na jednym koncie); liczą się pozostałe role
        if (roles.contains(AppRole.TK_ADMIN) && roles.stream().allMatch(r -> r == AppRole.TK_ADMIN)) {
            return false;
        }
        if (roles.contains(AppRole.TK_REFERENT) || roles.contains(AppRole.TK_KIEROWNIK) || roles.contains(AppRole.TK_PREZES)
                || roles.contains(AppRole.TK_ARCHIWISTA)) {
            return true;
        }
        return isTeamMember(userId, assignment);
    }

    /** Notatki wewnętrzne widzi tylko zespół sędziego (sędzia i jego asystenci) – FR-24, brief §5.2. */
    public static boolean canReadInternalNotes(String userId, Set<AppRole> roles, CaseAssignment assignment) {
        return (roles.contains(AppRole.TK_SEDZIA) || roles.contains(AppRole.TK_ASYSTENT) || roles.contains(AppRole.TK_PREZES))
                && isTeamMember(userId, assignment);
    }

    /** Czynność „za Prezesa” wymaga ważnego upoważnienia w dacie czynności (BR-10). */
    public static boolean canActAsPresident(String userId, Set<AppRole> roles, Set<Delegation> delegations, LocalDate day) {
        if (!roles.contains(AppRole.TK_PREZES) && !roles.contains(AppRole.TK_SEDZIA)) {
            return false;
        }
        return delegations.stream()
                .anyMatch(d -> d.userId().equals(userId) && FUNCTION_PRESIDENT.equals(d.function()) && d.validOn(day));
    }

    private static boolean isTeamMember(String userId, CaseAssignment a) {
        if (a == null || userId == null) {
            return false;
        }
        return userId.equals(a.judgeId()) || a.teamIds().contains(userId) || a.clerkIds().contains(userId);
    }
}
