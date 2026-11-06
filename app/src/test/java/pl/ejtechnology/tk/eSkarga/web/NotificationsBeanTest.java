package pl.ejtechnology.tk.eSkarga.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pl.ejtechnology.tk.eSkarga.support.Wiring;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;

/** Dzwonek: licznik = nieobejrzane w sesji; otwarcie panelu gasi licznik, zamknięcie go nie przywraca. */
class NotificationsBeanTest {

    @Test
    void licznikGasniePoOtwarciuPanelu() {
        NotificationsBean bean = Wiring.inject(new NotificationsBean(), new DemoDataset(), new AppClock(Clock.fixed(Instant.parse("2026-11-03T09:00:00Z"), ZoneOffset.UTC)));
        bean.setSince(Instant.EPOCH);
        assertThat(bean.getItems()).hasSize(3);
        assertThat(bean.getCount()).isEqualTo(3);
        assertThat(bean.isOpen()).isFalse();
        bean.toggle();
        assertThat(bean.isOpen()).isTrue();
        assertThat(bean.getCount()).as("po otwarciu wszystko obejrzane").isZero();
        bean.toggle();
        assertThat(bean.isOpen()).isFalse();
        assertThat(bean.getCount()).isZero();
        bean.close();
        assertThat(bean.isOpen()).isFalse();
    }
}
