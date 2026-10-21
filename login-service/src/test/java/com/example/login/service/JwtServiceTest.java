package com.example.login.service;

import com.example.login.config.JwtProperties;
import com.example.login.dto.response.LoginResponse;
import com.example.login.entity.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private KeyPair keyPair;
    private JwtProperties jwtProperties;
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("http://localhost:8083");
        jwtProperties.setAudience("jewelryshop-api");
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("jewelryshop-key-1")
                .build();
        jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<>(
                JwtClaimNames.ISS,
                iss -> iss != null && iss.equals(jwtProperties.getIssuer())
        );
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(jwtProperties.getAudience())
        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(timestampValidator, issuerValidator, audienceValidator)
        ));
        jwtDecoder = decoder;

        jwtService = new JwtService(jwtEncoder, jwtProperties);
    }

    @Test
    void generateLoginResponse_ShouldProduceTokenWithAudienceAndValidClaims() {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setEnabled(true);

        LoginResponse response = jwtService.generateLoginResponse(user);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("testuser", response.getUser().getUsername());

        Jwt jwt = jwtDecoder.decode(response.getAccessToken());
        assertEquals("http://localhost:8083", jwt.getIssuer().toString());
        assertTrue(jwt.getAudience().contains("jewelryshop-api"));
        assertEquals("testuser", jwt.getClaimAsString("username"));
        assertEquals(List.of("USER"), jwt.getClaimAsStringList("roles"));
        assertEquals("jewelryshop-key-1", jwt.getHeaders().get("kid"));
    }

    @Test
    void jwtDecoder_ShouldRejectToken_WhenAudienceIsMismatched() {
        // Build a decoder that expects a different audience
        NimbusJwtDecoder strictDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains("other-service-api")
        );
        strictDecoder.setJwtValidator(audienceValidator);

        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setEnabled(true);

        LoginResponse response = jwtService.generateLoginResponse(user);

        assertThrows(JwtValidationException.class, () -> strictDecoder.decode(response.getAccessToken()));
    }

    @Test
    void jwtDecoder_ShouldRejectToken_WhenIssuerIsMismatched() {
        NimbusJwtDecoder strictDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<>(
                JwtClaimNames.ISS,
                iss -> iss != null && iss.equals("https://auth.jewelryshop.com")
        );
        strictDecoder.setJwtValidator(issuerValidator);

        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setEnabled(true);

        LoginResponse response = jwtService.generateLoginResponse(user);

        assertThrows(JwtValidationException.class, () -> strictDecoder.decode(response.getAccessToken()));
    }
}
