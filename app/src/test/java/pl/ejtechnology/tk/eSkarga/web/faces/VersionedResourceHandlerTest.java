package pl.ejtechnology.tk.eSkarga.web.faces;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.web.AppInfo;

/** Zasoby własne z parametrem wersji buildu (pamięć podręczna przeglądarki po wdrożeniu). */
class VersionedResourceHandlerTest {

    @Test
    void doklejaWersjeDoAdresuZasobuZParametramiIBez() {
        assertThat(VersionedResourceHandler.withVersion("/eskarga/jakarta.faces.resource/pdf-viewer.js.xhtml?ln=js", "abc1"))
                .isEqualTo("/eskarga/jakarta.faces.resource/pdf-viewer.js.xhtml?ln=js&v=abc1");
        assertThat(VersionedResourceHandler.withVersion("/eskarga/jakarta.faces.resource/eskarga.css.xhtml", "abc1"))
                .isEqualTo("/eskarga/jakarta.faces.resource/eskarga.css.xhtml?v=abc1");
        assertThat(VersionedResourceHandler.withVersion("/x?v=abc1", "abc1")).isEqualTo("/x?v=abc1");
        assertThat(VersionedResourceHandler.withVersion("/x", "")).isEqualTo("/x");
    }

    @Test
    void znacznikBuilduJestStalyWObrebieBuilduIKrotki() {
        assertThat(AppInfo.buildStamp()).isEqualTo(AppInfo.buildStamp()).matches("[0-9a-f]{1,8}");
        assertThat(VersionedResourceHandler.OWN_LIBRARIES).contains("js", "css", "tk").doesNotContain("primefaces");
    }
}
