package com.example.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CustomAuthenticationWebFilterTest {

    private ReactiveAuthenticationManager authenticationManager;
    private CustomAuthenticationWebFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(ReactiveAuthenticationManager.class);
        chain = mock(WebFilterChain.class);
        filter = new CustomAuthenticationWebFilter(authenticationManager);
        filter.setServerAuthenticationConverter(new CustomBearerTokenAuthenticationConverter());
    }

    @Test
    void filter_whenNoToken_continuesChainWithoutAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        given(chain.filter(any())).willReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void filter_whenValidToken_authenticatesAndContinuesChain() {
        String token = "valid-token-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Jwt jwt = new Jwt(
                token,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "admin", "roles", "ROLE_ADMIN")
        );
        Authentication successfulAuth = new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        given(authenticationManager.authenticate(any(BearerTokenAuthenticationToken.class)))
                .willReturn(Mono.just(successfulAuth));
        given(chain.filter(any())).willReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(authenticationManager).authenticate(any(BearerTokenAuthenticationToken.class));
        verify(chain).filter(exchange);
    }

    @Test
    void filter_whenAuthenticationFails_invokesFailureHandler() {
        String token = "invalid-token-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ServerAuthenticationFailureHandler failureHandler = mock(ServerAuthenticationFailureHandler.class);
        filter.setAuthenticationFailureHandler(failureHandler);

        given(authenticationManager.authenticate(any(BearerTokenAuthenticationToken.class)))
                .willReturn(Mono.error(new BadCredentialsException("Token is invalid")));
        given(failureHandler.onAuthenticationFailure(any(), any())).willReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(failureHandler).onAuthenticationFailure(any(), any(BadCredentialsException.class));
        verify(chain, never()).filter(exchange);
    }
}
