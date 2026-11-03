package pl.ejtechnology.tk.eSkarga.workers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyAndBackoffTest {

    @Test
    void keyIsStableForSameDelivery() {
        assertThat(new IdempotencyKey(Topics.DORECZENIE_POCZTOWE, "DOR-17").value())
                .isEqualTo(new IdempotencyKey(Topics.DORECZENIE_POCZTOWE, "DOR-17").value())
                .isEqualTo("doreczenie-pocztowe:DOR-17");
    }

    @Test
    void keyRejectsBlankDeliveryId() {
        assertThatThrownBy(() -> new IdempotencyKey(Topics.DORECZENIE_POCZTOWE, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backoffGrowsAndIsCapped() {
        assertThat(AbstractIdempotentHandler.backoffMs(0)).isEqualTo(60_000L);
        assertThat(AbstractIdempotentHandler.backoffMs(1)).isEqualTo(120_000L);
        assertThat(AbstractIdempotentHandler.backoffMs(4)).isEqualTo(960_000L);
        assertThat(AbstractIdempotentHandler.backoffMs(10)).isEqualTo(3_600_000L);
    }
}
