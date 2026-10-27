package com.example.gateway.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayGlobalExceptionHandlerTest {

    private GatewayGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayGlobalExceptionHandler(new DefaultServerCodecConfigurer());
    }

    @Test
    void commence_returns401WithWwwAuthenticateHeaderAndCorrectJsonBody() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.commence(exchange, new BadCredentialsException("Invalid token"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("\"status\":401");
                    assertThat(body).contains("\"error\":\"Unauthorized\"");
                    assertThat(body).contains("\"message\":\"Authentication is required or the access token is invalid.\"");
                    assertThat(body).contains("\"path\":\"/api/v1/products\"");
                    assertThat(body).contains("\"validationErrors\":{}");
                    assertThat(body).contains("\"timestamp\":");
                })
                .verifyComplete();
    }

    @Test
    void handle_accessDenied_returns403WithCorrectJsonBody() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new AccessDeniedException("Access denied"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("\"status\":403");
                    assertThat(body).contains("\"error\":\"Forbidden\"");
                    assertThat(body).contains("\"message\":\"You do not have permission to access this resource.\"");
                    assertThat(body).contains("\"path\":\"/api/v1/products\"");
                })
                .verifyComplete();
    }

    @Test
    void handle_connectException_returns503ServiceUnavailableWithCorrectJsonBody() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new ConnectException("Connection refused"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("\"status\":503");
                    assertThat(body).contains("\"error\":\"Service Unavailable\"");
                    assertThat(body).contains("\"message\":\"A downstream service is unavailable. Please try again later.\"");
                    assertThat(body).doesNotContain("Connection refused"); // No internal details leaked
                })
                .verifyComplete();
    }

    @Test
    void handle_unknownHostException_returns503ServiceUnavailable() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new UnknownHostException("unknown-service"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handle_socketTimeoutException_returns504GatewayTimeoutWithCorrectJsonBody() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new SocketTimeoutException("Read timed out"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("\"status\":504");
                    assertThat(body).contains("\"error\":\"Gateway Timeout\"");
                    assertThat(body).contains("\"message\":\"A downstream service did not respond in time.\"");
                    assertThat(body).doesNotContain("Read timed out"); // No internal details leaked
                })
                .verifyComplete();
    }

    @Test
    void handle_timeoutException_returns504GatewayTimeout() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new TimeoutException("Channel timeout"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void handle_responseStatusException404_returns404NotFound() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/unknown-path").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handle_whenResponseAlreadyCommitted_doesNotAttemptToWriteAgain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Commit the response first
        exchange.getResponse().setComplete().block();
        assertThat(exchange.getResponse().isCommitted()).isTrue();

        ConnectException connectException = new ConnectException("Connection refused");
        Mono<Void> result = handler.handle(exchange, connectException);

        // When committed, handler should bubble up the original error without attempting to write to body
        StepVerifier.create(result)
                .expectErrorMatches(t -> t.equals(connectException))
                .verify();
    }
}
