package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.EnumSet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/** Wymuszenie logowania ID-users na stronach Facelets (D4, T-004): anonim → chroniony /logowanie (wyzwanie OIDC), zalogowany bez ról → 403. */
class LoginFilterTest {

    private final LoginFilter filter = new LoginFilter();
    private final HttpServletRequest req = mock(HttpServletRequest.class);
    private final HttpServletResponse resp = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        filter.settings = new OidcSettings() {
            @Override
            public boolean isDemo() { return false; }
        };
        filter.currentUser = mock(CurrentUser.class);
        filter.audit = mock(AuditService.class);
        when(req.getServletPath()).thenReturn("/pulpit.xhtml");
    }

    @Test
    void anonimowyPodmiotKonteneraIdzieNaChronionyWyzwalaczLogowania() throws Exception {
        Principal anon = () -> "anonymous";
        when(req.getUserPrincipal()).thenReturn(anon);
        when(req.getContextPath()).thenReturn("/eskarga");
        when(req.getRequestURI()).thenReturn("/eskarga/sprawy/repertorium.xhtml");
        when(req.getQueryString()).thenReturn("rok=2026");
        HttpSession session = mock(HttpSession.class);
        when(req.getSession(true)).thenReturn(session);
        filter.doFilter(req, resp, chain);
        verify(session).setAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE, "/sprawy/repertorium.xhtml?rok=2026");
        verify(resp).sendRedirect("/eskarga/logowanie");
        verify(req, never()).authenticate(any());
        verify(chain, never()).doFilter(any(), any());
        assertThat(LoginFilter.authenticated(anon)).isFalse();
    }

    @Test
    void wyzwalaczWracaNaZapamietanyAdresAlboPulpit() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE)).thenReturn("/sprawy/repertorium.xhtml?rok=2026");
        assertThat(LoginTriggerServlet.target(session)).isEqualTo("/sprawy/repertorium.xhtml?rok=2026");
        verify(session).removeAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE);
        assertThat(LoginTriggerServlet.target(null)).isEqualTo("/pulpit.xhtml");
        HttpSession obcy = mock(HttpSession.class);
        when(obcy.getAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE)).thenReturn("//zly.example/x");
        assertThat(LoginTriggerServlet.target(obcy)).isEqualTo("/pulpit.xhtml");
        HttpSession petla = mock(HttpSession.class);
        when(petla.getAttribute(LoginTriggerServlet.TARGET_ATTRIBUTE)).thenReturn("/logowanie");
        assertThat(LoginTriggerServlet.target(petla)).isEqualTo("/pulpit.xhtml");
    }

    @Test
    void zalogowanyBezRolEskargiDostaje403() throws Exception {
        when(req.getUserPrincipal()).thenReturn(() -> "anna.nowak");
        when(filter.currentUser.getRoles()).thenReturn(EnumSet.noneOf(AppRole.class));
        RequestDispatcher rd = mock(RequestDispatcher.class);
        when(req.getRequestDispatcher(LoginFilter.FORBIDDEN_PAGE)).thenReturn(rd);
        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(rd).forward(req, resp);
        verify(chain, never()).doFilter(any(), any());
        verify(filter.audit).recordDenial(eq("anna.nowak"), any(), any(), eq("/pulpit.xhtml"), any(), any());
    }

    @Test
    void zalogowanyZRolaPrzechodzi() throws Exception {
        when(req.getUserPrincipal()).thenReturn(() -> "anna.nowak");
        when(filter.currentUser.getRoles()).thenReturn(EnumSet.of(AppRole.TK_BIURO_PODAWCZE));
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        verify(req, never()).authenticate(any());
    }

    @Test
    void stronyBleduIZasobyJsfBezLogowania() throws Exception {
        when(req.getServletPath()).thenReturn("/blad/403.xhtml");
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        verify(req, never()).authenticate(any());
    }
}
