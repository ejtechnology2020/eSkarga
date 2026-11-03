package pl.ejtechnology.tk.eSkarga.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Mapowanie ról z tokenu ID-users na role aplikacji (T-004, kontrakt 03.11.2026): role klienta pod
 * {@code resource_access.<client_id>.roles}, role realmu pod {@code realm_access.roles}; opcjonalny prefiks ({@code eskarga.oidc.rolePrefix}).
 * Nieznane role są pomijane – aplikacja zna tylko {@link AppRole}.
 */
public final class RoleMapper {

    private RoleMapper() {
    }

    public static Set<AppRole> roles(JsonObject claims, String rolesPath, String realmRolesPath, String prefix) {
        EnumSet<AppRole> out = EnumSet.noneOf(AppRole.class);
        for (String name : strings(claims, rolesPath)) {
            map(name, prefix).ifPresent(out::add);
        }
        if (realmRolesPath != null && !realmRolesPath.isBlank()) {
            for (String name : strings(claims, realmRolesPath)) {
                map(name, prefix).ifPresent(out::add);
            }
        }
        return out;
    }

    static java.util.Optional<AppRole> map(String name, String prefix) {
        String n = name;
        if (prefix != null && !prefix.isBlank() && n.startsWith(prefix)) {
            n = n.substring(prefix.length());
        }
        for (AppRole r : AppRole.values()) {
            if (r.name().equalsIgnoreCase(n) || r.name().equalsIgnoreCase("TK_" + n)) {
                return java.util.Optional.of(r);
            }
        }
        return java.util.Optional.empty();
    }

    /** Wartości tekstowe pod ścieżką z kropkami, np. {@code resource_access.eskarga.roles}. */
    public static List<String> strings(JsonObject claims, String path) {
        if (claims == null || path == null || path.isBlank()) {
            return List.of();
        }
        JsonValue v = claims;
        for (String part : path.split("\\.")) {
            if (v == null || v.getValueType() != JsonValue.ValueType.OBJECT) {
                return List.of();
            }
            v = ((JsonObject) v).get(part);
        }
        if (v == null) {
            return List.of();
        }
        if (v.getValueType() == JsonValue.ValueType.ARRAY) {
            return ((JsonArray) v).getValuesAs(JsonString.class).stream().map(JsonString::getString).toList();
        }
        if (v.getValueType() == JsonValue.ValueType.STRING) {
            return List.of(((JsonString) v).getString().split("[ ,]+"));
        }
        return List.of();
    }
}
