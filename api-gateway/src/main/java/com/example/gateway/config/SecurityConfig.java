
package com.example.gateway.config;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import com.example.gateway.exception.GatewayGlobalExceptionHandler;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            GatewayGlobalExceptionHandler gatewayExceptionHandler
    ) {
        JwtReactiveAuthenticationManager authenticationManager =
                new JwtReactiveAuthenticationManager(jwtDecoder);
        authenticationManager.setJwtAuthenticationConverter(
                new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter)
        );

        CustomAuthenticationWebFilter authenticationFilter =
                new CustomAuthenticationWebFilter(authenticationManager);
        authenticationFilter.setServerAuthenticationConverter(
                new CustomBearerTokenAuthenticationConverter()
        );
        authenticationFilter.setSecurityContextRepository(
                NoOpServerSecurityContextRepository.getInstance()
        );
        authenticationFilter.setAuthenticationFailureHandler(
                new ServerAuthenticationEntryPointFailureHandler(
                        gatewayExceptionHandler
                )
        );

        return http
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .authorizeExchange(exchanges -> exchanges
                        // Public authentication endpoints
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Health check
                        .pathMatchers("/actuator/health").permitAll()

                        // Authenticated endpoint
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/me"
                        ).authenticated()

                        // Product: USER and ADMIN can read
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasAnyRole("USER", "ADMIN")

                        // Product: ADMIN only for write operations
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.PUT,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        .pathMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasRole("ADMIN")

                        // Inventory summaries are not exposed through Gateway
                        .pathMatchers(
                                "/api/v1/inventory-summaries",
                                "/api/v1/inventory-summaries/**"
                        ).denyAll()

                        // All inventory-item endpoints: ADMIN only
                        .pathMatchers(
                                "/api/v1/inventory-items",
                                "/api/v1/inventory-items/**"
                        ).hasRole("ADMIN")

                        // Orders: USER and ADMIN can create, read, and cancel orders
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/orders",
                                "/api/v1/orders/*/cancellation"
                        ).hasAnyRole("USER", "ADMIN")

                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/orders",
                                "/api/v1/orders/**"
                        ).hasAnyRole("USER", "ADMIN")

                        // Fail closed for unknown endpoints
                        .anyExchange().denyAll()
                )

                .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(gatewayExceptionHandler)
                        .accessDeniedHandler(gatewayExceptionHandler)
                )
                .build();
    }


    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${app.jwt.public-key-location}") Resource publicKeyResource,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) throws IOException {

        RSAPublicKey publicKey;

        try (var inputStream = publicKeyResource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(inputStream);
        }

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withPublicKey(publicKey)
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();

        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "JWT audience is invalid",
                    null
            );

            return OAuth2TokenValidatorResult.failure(error);
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerAndTimestampValidator,
                        audienceValidator
                )
        );

        return decoder;
    }
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }
}
