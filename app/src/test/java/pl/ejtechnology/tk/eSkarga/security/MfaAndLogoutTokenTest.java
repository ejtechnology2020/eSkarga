package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/** T-004: polityka MFA z konfigurowalnym claimem i walidacja logout_token (back-channel). */
class MfaAndLogoutTokenTest {

    private static JsonObject json(String s) {
        return Json.createReader(new StringReader(s)).readObject();
    }

    @Test
    void acrJakoTekstIAmrJakoTablica() {
        assertThat(MfaPolicy.satisfied(json("{\"acr\":\"urn:tk:acr:mfa\"}"), "acr", "urn:tk:acr:mfa")).isTrue();
        assertThat(MfaPolicy.satisfied(json("{\"acr\":\"1\"}"), "acr", "urn:tk:acr:mfa,2")).isFalse();
        assertThat(MfaPolicy.satisfied(json("{\"acr\":\"2\"}"), "acr", "urn:tk:acr:mfa,2")).isTrue();
        assertThat(MfaPolicy.satisfied(json("{\"amr\":[\"pwd\",\"otp\"]}"), "amr", "otp,hwk")).isTrue();
        assertThat(MfaPolicy.satisfied(json("{\"amr\":[\"pwd\"]}"), "amr", "otp,hwk")).isFalse();
        assertThat(MfaPolicy.satisfied(json("{\"sub\":\"x\"}"), "acr", "mfa")).as("brak claimu = brak MFA").isFalse();
        assertThat(MfaPolicy.satisfied(null, "acr", "mfa")).isFalse();
        assertThat(MfaPolicy.satisfied(json("{}"), "acr", "")).as("bez wymaganej wartości aplikacja nie wymusza MFA").isTrue();
    }

    @Test
    void logoutTokenWymagaZdarzeniaISidAlboSub() {
        assertThat(LogoutToken.target(json("{\"events\":{\"" + LogoutToken.EVENT + "\":{}},\"sid\":\"s1\",\"sub\":\"u1\"}")))
                .get().isEqualTo(new LogoutToken.Target("s1", "u1"));
        assertThat(LogoutToken.target(json("{\"events\":{\"" + LogoutToken.EVENT + "\":{}},\"sub\":\"u1\"}"))).get().isEqualTo(new LogoutToken.Target(null, "u1"));
        assertThat(LogoutToken.target(json("{\"events\":{\"inne\":{}},\"sid\":\"s1\"}"))).isEmpty();
        assertThat(LogoutToken.target(json("{\"events\":{\"" + LogoutToken.EVENT + "\":{}}}"))).as("bez sid i sub").isEmpty();
        assertThat(LogoutToken.target(json("{\"events\":{\"" + LogoutToken.EVENT + "\":{}},\"sid\":\"s1\",\"nonce\":\"n\"}"))).as("nonce zabronione").isEmpty();
    }
}
