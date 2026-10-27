package be.kdg.airfreight.flight.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {
    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToSpringAuthorities() {
        var token =
                token().claim(
                                "realm_access",
                                Map.of("roles", List.of("flight-planner", "load-planner")))
                        .build();
        assertThat(converter.convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_flight-planner", "ROLE_load-planner");
    }

    @Test
    void clientRolesDoNotGrantRealmPlannerPermissions() {
        var token =
                token().claim(
                                "resource_access",
                                Map.of("cargo-service", Map.of("roles", List.of("load-planner"))))
                        .build();
        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    void missingOrMalformedRoleCollectionsGrantNoAuthorities() {
        assertThat(converter.convert(token().build())).isEmpty();
        assertThat(converter.convert(token().claim("realm_access", Map.of()).build())).isEmpty();
        assertThat(
                        converter.convert(
                                token().claim("realm_access", Map.of("roles", "load-planner"))
                                        .build()))
                .isEmpty();
    }

    @Test
    void ignoresNonStringRoleEntries() {
        var token =
                token().claim("realm_access", Map.of("roles", List.of("load-planner", 12))).build();
        assertThat(converter.convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_load-planner");
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-id");
    }
}
