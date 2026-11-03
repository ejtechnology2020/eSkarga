package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.CorrectionStatus;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-22, FR-08, NFR-01: korekta wpisu bez UPDATE poprzednich wersji (T-014). */
class CorrectionServiceTest extends ServiceTestSupport {

    private CaseFile newCase() {
        long letterId = tx(s -> registerLetter(s)).getId();
        return tx(s -> createCase(s, letterId));
    }

    @Test
    void korektaWymagaUzasadnieniaIAkceptacjiKierownika() {
        CaseFile cf = newCase();
        assertThatThrownBy(() -> tx(s -> s.corrections.request(cf.getId(), new CorrectionService.Changes("inny przedmiot", null, null), " ", REFERENT)))
                .isInstanceOf(IllegalArgumentException.class);

        CaseFileVersion pending = tx(s -> s.corrections.request(cf.getId(), new CorrectionService.Changes("ustawa o przykładach art. 7", null, null),
                "błędnie wpisany przepis", REFERENT));
        assertThat(pending.getCorrectionStatus()).isEqualTo(CorrectionStatus.OCZEKUJE);
        tx(s -> assertThat(s.cases.require(cf.getId()).current().getVersion()).as("oczekująca korekta nie obowiązuje").isEqualTo(1));

        assertThatThrownBy(() -> run(s -> s.corrections.approve(pending.getId(), "asystent", Set.of(AppRole.TK_ASYSTENT))))
                .isInstanceOf(SecurityException.class);
        assertThat(auditCount("odmowa: akceptacja korekty")).isEqualTo(1);
        assertThatThrownBy(() -> run(s -> s.corrections.approve(pending.getId(), REFERENT, Set.of(AppRole.TK_KIEROWNIK))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("autor");

        run(s -> s.corrections.approve(pending.getId(), KIEROWNIK, Set.of(AppRole.TK_KIEROWNIK)));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.current().getVersion()).isEqualTo(2);
            assertThat(loaded.current().getReviewSubject()).isEqualTo("ustawa o przykładach art. 7");
            assertThat(loaded.current().getApprovedBy()).isEqualTo(KIEROWNIK);
            assertThat(s.corrections.history(cf.getId())).extracting(CaseFileVersion::getVersion).containsExactly(1, 2);
            assertThat(loaded.getVersions().get(0).getReviewSubject()).startsWith("ustawa z 1 stycznia 2020");
        });
    }

    @Test
    void odrzuconaKorektaZostajeWHistoriiAleNieObowiazuje() {
        CaseFile cf = newCase();
        CaseFileVersion pending = tx(s -> s.corrections.request(cf.getId(), new CorrectionService.Changes(null, "art. 2 Konstytucji", null), "zmiana wzorca", REFERENT));
        assertThatThrownBy(() -> tx(s -> s.corrections.request(cf.getId(), new CorrectionService.Changes(null, "x", null), "druga", REFERENT)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run(s -> s.corrections.reject(pending.getId(), KIEROWNIK, ""))).isInstanceOf(IllegalArgumentException.class);
        run(s -> s.corrections.reject(pending.getId(), KIEROWNIK, "wzorzec wynika ze skargi"));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            assertThat(loaded.current().getVersion()).isEqualTo(1);
            assertThat(s.corrections.history(cf.getId())).hasSize(2);
            assertThat(s.corrections.history(cf.getId()).get(1).getCorrectionStatus()).isEqualTo(CorrectionStatus.ODRZUCONA);
            assertThat(s.corrections.pendingAll()).isEmpty();
        });
        // kolejna zmiana statusu dostaje numer wyższy od odrzuconej wersji
        run(s -> s.cases.changeStatus(cf.getId(), pl.ejtechnology.tk.eSkarga.domain.CaseStatus.OCZEKUJE_NA_WYZNACZENIE_SEDZIEGO, REFERENT));
        tx(s -> assertThat(s.cases.require(cf.getId()).current().getVersion()).isEqualTo(3));
    }
}
