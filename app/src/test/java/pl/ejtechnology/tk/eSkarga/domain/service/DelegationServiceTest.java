package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.model.Delegation;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** FR-36 / S-23: upoważnienie z aktem podstawowym w repozytorium (klucz, SHA-256), wymagane pola, odczyt po identyfikatorze. */
class DelegationServiceTest extends ServiceTestSupport {

    @Test
    void upowaznienieZAktemWRepozytoriumZapisujeKluczISkrot() {
        Delegation d = tx(s -> s.delegations.add("sedzia.testowy", CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                "zarzadzenie-12-2026.pdf", "upowaznienia/2026/abc.pdf", "0".repeat(64), "admin.wszystko"));
        run(s -> {
            Delegation loaded = s.delegations.byId(d.getId()).orElseThrow();
            assertThat(loaded.getBasisDocRef()).isEqualTo("zarzadzenie-12-2026.pdf");
            assertThat(loaded.getBasisDocKey()).isEqualTo("upowaznienia/2026/abc.pdf");
            assertThat(loaded.getBasisDocSha256()).hasSize(64);
            assertThat(s.delegations.policyDelegations("sedzia.testowy")).singleElement().satisfies(p -> assertThat(p.function()).isEqualTo(CaseAccessPolicy.FUNCTION_PRESIDENT));
        });
    }

    @Test
    void brakOsobyOkresuAlboAktuJestOdrzucany() {
        assertThatThrownBy(() -> tx(s -> s.delegations.add(" ", "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "akt.pdf", "admin"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx(s -> s.delegations.add("ktos", "x", null, LocalDate.of(2026, 2, 1), "akt.pdf", "admin"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx(s -> s.delegations.add("ktos", "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "", "admin"))).isInstanceOf(IllegalArgumentException.class);
    }
}
