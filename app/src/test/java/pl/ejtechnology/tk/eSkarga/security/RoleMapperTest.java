package pl.ejtechnology.tk.eSkarga.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/** T-004 (kontrakt ID-users): role klienta pod resource_access.<client>.roles, role realmu pod realm_access.roles, prefiks opcjonalny. */
class RoleMapperTest {

    private static JsonObject json(String s) {
        return Json.createReader(new StringReader(s)).readObject();
    }

    @Test
    void roleKlientaIRealmu() {
        JsonObject claims = json("{\"sub\":\"u1\",\"resource_access\":{\"eskarga\":{\"roles\":[\"TK_REFERENT\",\"nieznana\"]},\"inna\":{\"roles\":[\"TK_ADMIN\"]}},"
                + "\"realm_access\":{\"roles\":[\"TK_SEDZIA\",\"offline_access\"]}}");
        assertThat(RoleMapper.roles(claims, "resource_access.eskarga.roles", "realm_access.roles", "")).containsExactlyInAnyOrder(AppRole.TK_REFERENT, AppRole.TK_SEDZIA);
        assertThat(RoleMapper.roles(claims, "resource_access.eskarga.roles", null, "")).containsExactly(AppRole.TK_REFERENT);
        assertThat(RoleMapper.roles(claims, "resource_access.brak.roles", "", "")).isEmpty();
        assertThat(RoleMapper.roles(json("{\"roles\":\"ESKARGA_TK_PREZES ESKARGA_TK_SEDZIA\"}"), "roles", "", "ESKARGA_")).containsExactlyInAnyOrder(AppRole.TK_PREZES, AppRole.TK_SEDZIA);
        assertThat(RoleMapper.roles(json("{\"resource_access\":{\"eskarga\":{\"roles\":[\"referent\"]}}}"), "resource_access.eskarga.roles", "", "")).containsExactly(AppRole.TK_REFERENT);
        assertThat(RoleMapper.strings(null, "a.b")).isEmpty();
    }
}
