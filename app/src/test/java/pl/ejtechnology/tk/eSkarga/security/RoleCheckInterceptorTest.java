package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.audit.AuditEvent;

/** Obrona w głąb na usługach: rola z CurrentUser, API workerów, wywołania systemowe, odmowa w dzienniku zdarzeń. */
class RoleCheckInterceptorTest {

    @RequiresRole({AppRole.TK_ADMIN})
    static class Usluga {
        boolean wykonano;

        public void zmien() { wykonano = true; }

        @RequiresRole
        public void czytaj() { wykonano = true; }

        @RequiresRole(value = {AppRole.TK_REFERENT}, workers = true)
        public void zWorkera() { wykonano = true; }

        @RequiresRole(system = true)
        public void systemowa() { wykonano = true; }
    }

    static final class Osoba extends CurrentUser {
        private final String login;
        private final Set<AppRole> roles;
        private final boolean bezKontekstu;

        Osoba(String login, Set<AppRole> roles, boolean bezKontekstu) {
            this.login = login;
            this.roles = roles;
            this.bezKontekstu = bezKontekstu;
        }

        @Override
        public boolean isAuthenticated() {
            if (bezKontekstu) {
                throw new ContextNotActiveException("brak sesji");
            }
            return login != null;
        }

        @Override
        public Set<AppRole> getRoles() { return roles; }

        @Override
        public String getLogin() { return login; }
    }

    static final class Dziennik extends AuditService {
        final List<String> odmowy = new ArrayList<>();

        @Override
        public AuditEvent recordDenial(String userId, String userRole, String action, String objectRef, String caseRef, String remoteAddr) {
            odmowy.add(userId + " [" + userRole + "] " + action);
            return null;
        }
    }

    private static InvocationContext wywolanie(Usluga cel, String metoda) throws NoSuchMethodException {
        Method m = Usluga.class.getMethod(metoda);
        return new InvocationContext() {
            @Override public Object getTarget() { return cel; }
            @Override public Object getTimer() { return null; }
            @Override public Method getMethod() { return m; }
            @Override public Constructor<?> getConstructor() { return null; }
            @Override public Object[] getParameters() { return new Object[0]; }
            @Override public void setParameters(Object[] params) { }
            @Override public Map<String, Object> getContextData() { return Map.of(); }
            @Override public Object proceed() throws Exception { m.invoke(cel); return null; }
        };
    }

    private static RoleCheckInterceptor interceptor(CurrentUser user, boolean worker, Dziennik dziennik) {
        RoleCheckInterceptor i = new RoleCheckInterceptor();
        i.currentUser = user;
        i.worker = new WorkerContext();
        if (worker) {
            i.worker.activate("eskarga-workers");
        }
        i.audit = dziennik;
        return i;
    }

    @Test
    void regulaZKlasyIMetody() throws Exception {
        Dziennik dziennik = new Dziennik();
        Usluga u = new Usluga();
        interceptor(new Osoba("admin", EnumSet.of(AppRole.TK_ADMIN), false), false, dziennik).check(wywolanie(u, "zmien"));
        assertThat(u.wykonano).isTrue();

        Usluga r = new Usluga();
        RoleCheckInterceptor referent = interceptor(new Osoba("jtestowy", EnumSet.of(AppRole.TK_REFERENT), false), false, dziennik);
        assertThatThrownBy(() -> referent.check(wywolanie(r, "zmien"))).isInstanceOf(SecurityException.class).hasMessageContaining("TK_ADMIN");
        assertThat(dziennik.odmowy).hasSize(1);
        assertThat(dziennik.odmowy.get(0)).contains("jtestowy").contains("TK_REFERENT").contains("Usluga.zmien");
        referent.check(wywolanie(r, "czytaj"));
        assertThat(r.wykonano).as("adnotacja metody (dowolna rola) zastępuje regułę klasy").isTrue();
    }

    @Test
    void bezRoliIBezKontekstu() throws Exception {
        Dziennik dziennik = new Dziennik();
        Usluga u = new Usluga();
        RoleCheckInterceptor anonim = interceptor(new Osoba(null, EnumSet.noneOf(AppRole.class), false), false, dziennik);
        assertThatThrownBy(() -> anonim.check(wywolanie(u, "czytaj"))).isInstanceOf(SecurityException.class).hasMessageContaining("brak zalogowanej osoby");
        RoleCheckInterceptor watek = interceptor(new Osoba(null, Set.of(), true), false, dziennik);
        assertThatThrownBy(() -> watek.check(wywolanie(u, "zmien"))).as("wątek bez sesji nie wykona metody użytkownika").isInstanceOf(SecurityException.class);
        watek.check(wywolanie(u, "systemowa"));
        assertThat(u.wykonano).as("system = true: bez kontroli ról").isTrue();
        assertThat(dziennik.odmowy).hasSize(2);
        assertThat(dziennik.odmowy.get(1)).contains("system").contains("bez kontekstu użytkownika");
    }

    /** Usługa → usługa w jednej operacji (podpis → wyznaczenie → zmiana statusu, API workera → termin): bez ponownej kontroli ról. */
    @Test
    void wywolaniaZagniezdzoneDziedziczaDopuszczenie() throws Exception {
        Dziennik dziennik = new Dziennik();
        Usluga zewnetrzna = new Usluga();
        Usluga wewnetrzna = new Usluga();
        RoleCheckInterceptor worker = interceptor(new Osoba(null, Set.of(), false), true, dziennik);
        Method m = Usluga.class.getMethod("zWorkera");
        InvocationContext zagniezdzone = wywolanie(wewnetrzna, "zmien");   // TK_ADMIN – worker sam w sobie by nie przeszedł
        InvocationContext zewnetrzneCtx = new InvocationContext() {
            @Override public Object getTarget() { return zewnetrzna; }
            @Override public Object getTimer() { return null; }
            @Override public Method getMethod() { return m; }
            @Override public Constructor<?> getConstructor() { return null; }
            @Override public Object[] getParameters() { return new Object[0]; }
            @Override public void setParameters(Object[] params) { }
            @Override public Map<String, Object> getContextData() { return Map.of(); }
            @Override public Object proceed() throws Exception { m.invoke(zewnetrzna); return worker.check(zagniezdzone); }
        };
        worker.check(zewnetrzneCtx);
        assertThat(zewnetrzna.wykonano).isTrue();
        assertThat(wewnetrzna.wykonano).as("wywołanie zagnieżdżone przeszło bez własnej kontroli").isTrue();
        assertThat(dziennik.odmowy).isEmpty();
        Usluga osobne = new Usluga();
        assertThatThrownBy(() -> worker.check(wywolanie(osobne, "zmien"))).as("po zakończeniu operacji licznik głębokości wraca do zera").isInstanceOf(SecurityException.class);
    }

    @Test
    void apiWorkerow() throws Exception {
        Usluga u = new Usluga();
        RoleCheckInterceptor worker = interceptor(new Osoba(null, Set.of(), false), true, new Dziennik());
        worker.check(wywolanie(u, "zWorkera"));
        assertThat(u.wykonano).isTrue();
        Usluga v = new Usluga();
        assertThatThrownBy(() -> worker.check(wywolanie(v, "zmien"))).as("worker nie wykona metody bez workers = true").isInstanceOf(SecurityException.class);
        Usluga w = new Usluga();
        RoleCheckInterceptor sedzia = interceptor(new Osoba("sedzia", EnumSet.of(AppRole.TK_SEDZIA), false), false, new Dziennik());
        assertThatThrownBy(() -> sedzia.check(wywolanie(w, "zWorkera"))).as("workers = true nie poszerza ról użytkowników").isInstanceOf(SecurityException.class);
    }
}
