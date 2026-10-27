package com.example.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenValidationTest {

    private static final String EXPECTED_ISSUER = "http://localhost:8083";
    private static final String EXPECTED_AUDIENCE = "jewelryshop-api";

    private OAuth2TokenValidator<Jwt> delegatingValidator;

    @BeforeEach
    void setUp() {
        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(EXPECTED_ISSUER);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(EXPECTED_AUDIENCE)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "JWT audience is invalid", null);
            return OAuth2TokenValidatorResult.failure(error);
        };

        delegatingValidator = new DelegatingOAuth2TokenValidator<>(
                issuerAndTimestampValidator,
                audienceValidator
        );
    }

    @Test
    void validate_whenJwtValid_returnsSuccess() {
        Jwt jwt = createJwt(
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        OAuth2TokenValidatorResult result = delegatingValidator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void validate_whenWrongIssuer_returnsFailure() {
        Jwt jwt = createJwt(
                "http://rogue-auth-server:9999",
                List.of(EXPECTED_AUDIENCE),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        OAuth2TokenValidatorResult result = delegatingValidator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(err -> err.getDescription().contains("iss"));
    }

    @Test
    void validate_whenWrongAudience_returnsFailure() {
        Jwt jwt = createJwt(
                EXPECTED_ISSUER,
                List.of("different-audience"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        OAuth2TokenValidatorResult result = delegatingValidator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(err -> err.getDescription().contains("JWT audience is invalid"));
    }

    @Test
    void validate_whenNullAudience_returnsFailure() {
        Jwt jwt = createJwt(
                EXPECTED_ISSUER,
                null,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        OAuth2TokenValidatorResult result = delegatingValidator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(err -> err.getDescription().contains("JWT audience is invalid"));
    }

    @Test
    void validate_whenExpired_returnsFailure() {
        Jwt jwt = createJwt(
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600) // Expired 1 hour ago
        );

        OAuth2TokenValidatorResult result = delegatingValidator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(err -> err.getDescription().contains("expired"));
    }

    private Jwt createJwt(String issuer, List<String> audience, Instant issuedAt, Instant expiresAt) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", "test-user");
        if (issuer != null) {
            claims.put("iss", issuer);
        }
        if (audience != null) {
            claims.put("aud", audience);
        }
        claims.put("iat", issuedAt);
        claims.put("exp", expiresAt);

        return new Jwt("mock-token-value", issuedAt, expiresAt, headers, claims);
    }
}
