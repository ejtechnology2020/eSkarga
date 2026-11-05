package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.domain.model.KnownUser;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** Rejestr osób znanych eSkardze (S-22, T-004): wpis przy logowaniu, aktualizacja ról i licznika, bez duplikatów loginu. */
class UserDirectoryServiceTest extends ServiceTestSupport {

    @Test
    void logowanieZapisujeOsobeIAktualizujeRole() {
        KnownUser pierwszy = tx(s -> s.directory.touch("anna.testowa", "Anna Testowa", EnumSet.of(AppRole.TK_BIURO_PODAWCZE)));
        assertThat(pierwszy.getLoginCount()).isEqualTo(1);
        assertThat(pierwszy.getRoles()).isEqualTo("TK_BIURO_PODAWCZE");
        KnownUser drugi = tx(s -> s.directory.touch("anna.testowa", null, EnumSet.of(AppRole.TK_KIEROWNIK, AppRole.TK_REFERENT)));
        assertThat(drugi.getId()).isEqualTo(pierwszy.getId());
        assertThat(drugi.getLoginCount()).isEqualTo(2);
        assertThat(drugi.getDisplayName()).isEqualTo("Anna Testowa");
        assertThat(drugi.getRoles()).isEqualTo("TK_REFERENT,TK_KIEROWNIK");
        assertThat(tx(s -> s.directory.all()).stream().filter(u -> u.getLogin().equals("anna.testowa")).count()).isEqualTo(1);
    }
}
