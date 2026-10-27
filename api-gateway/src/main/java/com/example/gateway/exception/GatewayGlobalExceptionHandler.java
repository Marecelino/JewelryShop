package com.example.gateway.exception;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayGlobalExceptionHandler
        implements ErrorWebExceptionHandler,
                   ServerAuthenticationEntryPoint,
                   ServerAccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayGlobalExceptionHandler.class);

    private final ServerResponse.Context responseContext;

    public GatewayGlobalExceptionHandler(
            ServerCodecConfigurer codecConfigurer
    ) {
        this.responseContext = new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return codecConfigurer.getWriters();
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return List.of();
            }
        };
    }

    /**
     * Handles uncaught WebFlux/Gateway exceptions.
     */
    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable exception
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        ErrorDetails details = resolveError(exception);

        String path = exchange.getRequest().getURI().getPath();

        if (details.status().is5xxServerError()) {
            log.error(
                    "Gateway error on [{} {}], returning HTTP {}",
                    exchange.getRequest().getMethod(),
                    path,
                    details.status().value(),
                    exception
            );
        } else {
            log.debug(
                    "Gateway handled exception on [{} {}], HTTP {}",
                    exchange.getRequest().getMethod(),
                    path,
                    details.status().value()
            );
        }

        return writeError(
                exchange,
                details.status(),
                details.message()
        );
    }

    /**
     * Handles unauthenticated requests and invalid Bearer tokens.
     */
    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception
    ) {
        log.debug(
                "Authentication rejected for [{} {}]",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath()
        );

        return writeError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "Authentication is required or the access token is invalid."
        );
    }

    /**
     * Handles authenticated requests that lack the required permission.
     */
    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception
    ) {
        log.debug(
                "Access denied for [{} {}]",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath()
        );

        return writeError(
                exchange,
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource."
        );
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        GatewayApiErrorResponse body = new GatewayApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath(),
                Map.of()
        );

        ServerResponse.BodyBuilder responseBuilder =
                ServerResponse.status(status)
                        .contentType(MediaType.APPLICATION_JSON);

        if (status == HttpStatus.UNAUTHORIZED) {
            responseBuilder.header(
                    HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer"
            );
        }

        return responseBuilder
                .bodyValue(body)
                .flatMap(response ->
                        response.writeTo(exchange, responseContext)
                );
    }

    private ErrorDetails resolveError(Throwable exception) {
        if (isUpstreamTimeout(exception)) {
            return new ErrorDetails(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "A downstream service did not respond in time."
            );
        }

        if (isUpstreamUnavailable(exception)) {
            return new ErrorDetails(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A downstream service is unavailable. Please try again later."
            );
        }

        if (exception instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(
                    statusException.getStatusCode().value()
            );

            if (status != null) {
                return new ErrorDetails(status, safeMessage(status));
            }
        }

        return new ErrorDetails(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support."
        );
    }

    private boolean isUpstreamTimeout(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }

            String className = current.getClass().getSimpleName();
            if (className.contains("ResponseTimeoutException")
                    || className.contains("ReadTimeoutException")) {
                return true;
            }

            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }

        return false;
    }

    private boolean isUpstreamUnavailable(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException) {
                return true;
            }

            // Recognize common Reactor Netty connection establishment failures.
            String className = current.getClass().getSimpleName();
            if (className.contains("ConnectTimeoutException")
                    || className.contains("AnnotatedConnectException")
                    || className.contains("PrematureCloseException")) {
                return true;
            }

            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }

        return false;
    }

    private String safeMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST ->
                    "The request is invalid.";
            case NOT_FOUND ->
                    "The requested resource was not found.";
            case METHOD_NOT_ALLOWED ->
                    "The HTTP method is not supported for this endpoint.";
            case UNAUTHORIZED ->
                    "Authentication is required or the access token is invalid.";
            case FORBIDDEN ->
                    "You do not have permission to access this resource.";
            case CONFLICT ->
                    "The request conflicts with the current resource state.";
            case BAD_GATEWAY ->
                    "An upstream service returned an invalid response.";
            case SERVICE_UNAVAILABLE ->
                    "A downstream service is unavailable. Please try again later.";
            case GATEWAY_TIMEOUT ->
                    "A downstream service did not respond in time.";
            default -> status.is4xxClientError()
                    ? "The request could not be processed."
                    : "An unexpected error occurred. Please contact support.";
        };
    }

    private record ErrorDetails(
            HttpStatus status,
            String message
    ) {
    }
}
