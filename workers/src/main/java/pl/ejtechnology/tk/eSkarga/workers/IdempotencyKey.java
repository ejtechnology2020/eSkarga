package pl.ejtechnology.tk.eSkarga.workers;

import java.util.Objects;

/**
 * Klucz idempotencji wysyłki (D7): identyfikator doręczenia + temat. Ponowienie zadania nigdy nie tworzy duplikatu,
 * bo system zewnętrzny (albo zaślepka WireMock) odpowiada tym samym wynikiem dla tego samego klucza.
 */
public record IdempotencyKey(String topic, String deliveryId) {

    public IdempotencyKey {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(deliveryId);
        if (deliveryId.isBlank()) {
            throw new IllegalArgumentException("Identyfikator doręczenia nie może być pusty");
        }
    }

    public String value() {
        return topic + ":" + deliveryId;
    }
}
