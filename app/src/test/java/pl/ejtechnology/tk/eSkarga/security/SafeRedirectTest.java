package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ASVS V14.5 (T-055): przekierowania tylko w obrębie aplikacji. */
class SafeRedirectTest {

    @Test
    void odrzucaAdresyZewnetrzneISztuczki() {
        assertThat(SafeRedirect.sanitize("/pulpit.xhtml")).contains("/pulpit.xhtml");
        assertThat(SafeRedirect.sanitize("/akta/podglad.xhtml?sygnatura=Ts%20312/26")).isPresent();
        assertThat(SafeRedirect.sanitize("https://zly.example/")).isEmpty();
        assertThat(SafeRedirect.sanitize("//zly.example/")).isEmpty();
        assertThat(SafeRedirect.sanitize("/\\zly.example")).isEmpty();
        assertThat(SafeRedirect.sanitize("/../etc/passwd")).isEmpty();
        assertThat(SafeRedirect.sanitize("javascript:alert(1)")).isEmpty();
        assertThat(SafeRedirect.sanitize("/pulpit.xhtml\r\nSet-Cookie: a=b")).isEmpty();
        assertThat(SafeRedirect.sanitize(null)).isEmpty();
        assertThat(SafeRedirect.orDefault("http://x", "/pulpit.xhtml")).isEqualTo("/pulpit.xhtml");
    }
}
