package com.example.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class CustomBearerTokenAuthenticationConverterTest {

    private CustomBearerTokenAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CustomBearerTokenAuthenticationConverter();
    }

    @Test
    void convert_whenNoAuthorizationHeader_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void convert_whenHeaderNotBearer_returnsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA==")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void convert_whenBearerTokenMalformed_returnsOAuth2AuthenticationException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token with invalid spaces")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof OAuth2AuthenticationException
                        && throwable.getMessage().contains("Bearer token is malformed"))
                .verify();
    }

    @Test
    void convert_whenValidBearerToken_returnsBearerTokenAuthenticationToken() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
                    BearerTokenAuthenticationToken bearerToken = (BearerTokenAuthenticationToken) auth;
                    assertThat(bearerToken.getToken()).isEqualTo(token);
                })
                .verifyComplete();
    }

    @Test
    void convert_whenBearerLowercase_returnsBearerTokenAuthenticationToken() {
        String token = "valid-token-123_456";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
                    BearerTokenAuthenticationToken bearerToken = (BearerTokenAuthenticationToken) auth;
                    assertThat(bearerToken.getToken()).isEqualTo(token);
                })
                .verifyComplete();
    }

    @Test
    void convert_whenUriQueryParameterAllowed_extractsToken() {
        converter.setAllowUriQueryParameter(true);
        String token = "query-param-token-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products?access_token=" + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
                    BearerTokenAuthenticationToken bearerToken = (BearerTokenAuthenticationToken) auth;
                    assertThat(bearerToken.getToken()).isEqualTo(token);
                })
                .verifyComplete();
    }

    @Test
    void convert_whenMultipleTokensPresent_returnsError() {
        converter.setAllowUriQueryParameter(true);
        String headerToken = "header-token-123";
        String queryToken = "query-token-456";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products?access_token=" + queryToken)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + headerToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof OAuth2AuthenticationException
                        && throwable.getMessage().contains("Found multiple bearer tokens in the request"))
                .verify();
    }

    @Test
    void convert_whenCustomHeaderConfigured_extractsToken() {
        converter.setBearerTokenHeaderName("X-Jewelry-Token");
        String token = "custom-header-token-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header("X-Jewelry-Token", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Authentication> result = converter.convert(exchange);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
                    BearerTokenAuthenticationToken bearerToken = (BearerTokenAuthenticationToken) auth;
                    assertThat(bearerToken.getToken()).isEqualTo(token);
                })
                .verifyComplete();
    }
}
