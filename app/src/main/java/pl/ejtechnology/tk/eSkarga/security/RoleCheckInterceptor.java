package pl.ejtechnology.tk.eSkarga.security;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Egzekwuje {@link RequiresRole} na usługach EJB: bez zalogowanej osoby z rolą eSkargi (albo kontekstu workera / systemu) wywołanie
 * kończy się {@link SecurityException}, a odmowa trafia do dziennika zdarzeń (NFR-02). Działa w obu trybach bezpieczeństwa,
 * bo role pochodzą z {@link CurrentUser}, nie z {@code @RolesAllowed} kontenera (ADR 0002: tryb demo nie ma tożsamości kontenera).
 */
@Interceptor
@RequiresRole
@Priority(Interceptor.Priority.APPLICATION + 10)
public class RoleCheckInterceptor {

    @Inject
    CurrentUser currentUser;
    @Inject
    WorkerContext worker;
    @Inject
    AuditService audit;

    /** Głębokość wywołań usług w bieżącym wątku: kontrola ról dotyczy wejścia (bean/serwlet/API → usługa), nie wywołań usługa → usługa. */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    @AroundInvoke
    public Object check(InvocationContext ctx) throws Exception {
        if (DEPTH.get() > 0) {
            return ctx.proceed();   // wywołanie zagnieżdżone w już dopuszczonej operacji (np. podpis → wyznaczenie → zmiana statusu)
        }
        RequiresRole rule = effective(ctx.getMethod(), ctx.getTarget() == null ? ctx.getMethod().getDeclaringClass() : ctx.getTarget().getClass());
        if (rule != null && !rule.system() && !(rule.workers() && isWorker())) {
            Set<AppRole> roles = userRoles();
            String operation = ctx.getMethod().getDeclaringClass().getSimpleName() + "." + ctx.getMethod().getName();
            if (roles == null || roles.isEmpty()) {
                deny(operation, rule, roles, "brak zalogowanej osoby z rolą eSkargi");
            }
            if (rule.value().length > 0 && Arrays.stream(rule.value()).noneMatch(roles::contains)) {
                deny(operation, rule, roles, "brak wymaganej roli");
            }
        }
        DEPTH.set(DEPTH.get() + 1);
        try {
            return ctx.proceed();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    /** Reguła z metody, a gdy jej nie ma – z klasy (także nadklasy proxy kontenera). */
    static RequiresRole effective(Method method, Class<?> targetClass) {
        RequiresRole m = method.getAnnotation(RequiresRole.class);
        if (m != null) {
            return m;
        }
        for (Class<?> c = targetClass; c != null && c != Object.class; c = c.getSuperclass()) {
            RequiresRole r = c.getAnnotation(RequiresRole.class);
            if (r != null) {
                return r;
            }
        }
        return method.getDeclaringClass().getAnnotation(RequiresRole.class);
    }

    static String required(RequiresRole rule) {
        return rule.value().length == 0 ? "dowolna rola eSkargi" : Arrays.stream(rule.value()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** Role zalogowanej osoby; {@code null} = brak kontekstu użytkownika (wątek bez sesji, np. wykonawca zadań procesu). */
    Set<AppRole> userRoles() {
        try {
            if (currentUser == null || !currentUser.isAuthenticated()) {
                return Set.of();
            }
            return currentUser.getRoles();
        } catch (ContextNotActiveException e) {
            return null;
        }
    }

    boolean isWorker() {
        try {
            return worker != null && worker.isActive();
        } catch (ContextNotActiveException e) {
            return false;
        }
    }

    private void deny(String operation, RequiresRole rule, Set<AppRole> roles, String reason) {
        String login = null;
        try {
            login = roles == null ? null : currentUser.getLogin();
        } catch (RuntimeException ignored) {
            // brak kontekstu – odmowa bez loginu
        }
        String held = roles == null ? "(bez kontekstu użytkownika)" : roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        if (audit != null) {
            try {
                audit.recordDenial(login == null ? "system" : login, held, "odmowa: " + operation + " wymaga roli " + required(rule), null, null, null);
            } catch (RuntimeException ignored) {
                // dziennik nie może zablokować odmowy
            }
        }
        throw new SecurityException("Brak uprawnień do operacji " + operation + " (" + reason + "; wymagana: " + required(rule) + ")");
    }
}
