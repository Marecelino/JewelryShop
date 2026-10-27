package com.example.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigRoleMappingTest {

    @Test
    void jwtAuthenticationConverter_mapsRolesWithRolePrefix() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = new Jwt(
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "test-user",
                        "roles", List.of("ADMIN", "USER")
                )
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        Collection<GrantedAuthority> authorities = auth.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void jwtAuthenticationConverter_whenNoRolesClaim_containsNoRoleAuthorities() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = new Jwt(
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "test-user")
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        Collection<GrantedAuthority> authorities = auth.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.startsWith("ROLE_"));
    }
}
