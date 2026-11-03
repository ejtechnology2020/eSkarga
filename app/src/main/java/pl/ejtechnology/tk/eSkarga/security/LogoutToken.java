package pl.ejtechnology.tk.eSkarga.security;

import java.util.Optional;

import jakarta.json.JsonObject;

/**
 * Walidacja {@code logout_token} (OpenID Connect Back-Channel Logout 1.0, §2.4 – §2.6): zdarzenie
 * {@code http://schemas.openid.net/event/backchannel-logout}, {@code sid} albo {@code sub}, brak {@code nonce}.
 * Podpis, {@code iss}, {@code aud} i {@code iat} sprawdza wcześniej weryfikator JWT.
 */
public final class LogoutToken {

    public static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

    /** Identyfikatory do unieważnienia sesji. */
    public record Target(String sid, String sub) {
    }

    private LogoutToken() {
    }

    public static Optional<Target> target(JsonObject payload) {
        if (payload == null || payload.containsKey("nonce")) {
            return Optional.empty();
        }
        JsonObject events = payload.getJsonObject("events");
        if (events == null || !events.containsKey(EVENT)) {
            return Optional.empty();
        }
        String sid = payload.getString("sid", null);
        String sub = payload.getString("sub", null);
        if (sid == null && sub == null) {
            return Optional.empty();
        }
        return Optional.of(new Target(sid, sub));
    }
}
