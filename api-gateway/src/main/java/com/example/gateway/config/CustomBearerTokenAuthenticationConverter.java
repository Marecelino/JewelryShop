package com.example.gateway.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom converter thay thế cho ServerBearerTokenAuthenticationConverter mặc định.
 * Giữ đầy đủ các bước tách token theo chuẩn RFC 6750 để dễ dàng đặt Breakpoint debug
 * và tùy chỉnh logic validate/extract token tại API Gateway.
 */
public class CustomBearerTokenAuthenticationConverter implements ServerAuthenticationConverter {

    private static final Logger log = LoggerFactory.getLogger(CustomBearerTokenAuthenticationConverter.class);

    private static final String ACCESS_TOKEN_PARAMETER_NAME = "access_token";

    private static final Pattern authorizationPattern = Pattern.compile(
            "^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$",
            Pattern.CASE_INSENSITIVE
    );

    private boolean allowFormEncodedBodyParameter = false;

    private boolean allowUriQueryParameter = false;

    private String bearerTokenHeaderName = HttpHeaders.AUTHORIZATION;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            ServerHttpRequest request = exchange.getRequest();
            log.debug("Converting request [{} {}] to BearerTokenAuthenticationToken",
                    request.getMethod(), request.getURI().getPath());

            return Flux.merge(
                            resolveFromAuthorizationHeader(request.getHeaders()),
                            resolveAccessTokenFromQueryString(request),
                            resolveAccessTokenFromBody(exchange)
                    )
                    .collectList()
                    .flatMap(CustomBearerTokenAuthenticationConverter::resolveToken)
                    .map(BearerTokenAuthenticationToken::new);
        });
    }

    private static Mono<String> resolveToken(List<String> accessTokens) {
        if (CollectionUtils.isEmpty(accessTokens)) {
            log.debug("No bearer token found in request (Header, Query, or Body)");
            return Mono.empty();
        }

        if (accessTokens.size() > 1) {
            log.warn("Rejected request: Found multiple bearer tokens ({} tokens) in the request", accessTokens.size());
            BearerTokenError error = BearerTokenErrors.invalidRequest("Found multiple bearer tokens in the request");
            return Mono.error(new OAuth2AuthenticationException(error));
        }

        String accessToken = accessTokens.get(0);
        if (!StringUtils.hasText(accessToken)) {
            log.warn("Rejected request: The requested token parameter is an empty string");
            BearerTokenError error = BearerTokenErrors
                    .invalidRequest("The requested token parameter is an empty string");
            return Mono.error(new OAuth2AuthenticationException(error));
        }

        log.debug("Resolved single valid bearer token string from request");
        return Mono.just(accessToken);
    }

    private Mono<String> resolveFromAuthorizationHeader(HttpHeaders headers) {
        String authorization = headers.getFirst(this.bearerTokenHeaderName);
        if (!StringUtils.startsWithIgnoreCase(authorization, "bearer")) {
            return Mono.empty();
        }

        Matcher matcher = authorizationPattern.matcher(authorization);
        if (!matcher.matches()) {
            log.warn("Rejected request: Bearer token in header '{}' is malformed", this.bearerTokenHeaderName);
            BearerTokenError error = BearerTokenErrors.invalidToken("Bearer token is malformed");
            throw new OAuth2AuthenticationException(error);
        }

        return Mono.just(matcher.group("token"));
    }

    private Flux<String> resolveAccessTokenFromQueryString(ServerHttpRequest request) {
        if (!this.allowUriQueryParameter || !HttpMethod.GET.equals(request.getMethod())) {
            return Flux.empty();
        }

        return resolveTokens(request.getQueryParams());
    }

    private Flux<String> resolveAccessTokenFromBody(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (!this.allowFormEncodedBodyParameter
                || !MediaType.APPLICATION_FORM_URLENCODED.equals(request.getHeaders().getContentType())
                || !HttpMethod.POST.equals(request.getMethod())) {
            return Flux.empty();
        }

        return exchange.getFormData().flatMapMany(CustomBearerTokenAuthenticationConverter::resolveTokens);
    }

    private static Flux<String> resolveTokens(MultiValueMap<String, String> parameters) {
        List<String> accessTokens = parameters.get(ACCESS_TOKEN_PARAMETER_NAME);
        return CollectionUtils.isEmpty(accessTokens) ? Flux.empty() : Flux.fromIterable(accessTokens);
    }

    public void setAllowUriQueryParameter(boolean allowUriQueryParameter) {
        this.allowUriQueryParameter = allowUriQueryParameter;
    }

    public void setBearerTokenHeaderName(String bearerTokenHeaderName) {
        this.bearerTokenHeaderName = bearerTokenHeaderName;
    }

    public void setAllowFormEncodedBodyParameter(boolean allowFormEncodedBodyParameter) {
        this.allowFormEncodedBodyParameter = allowFormEncodedBodyParameter;
    }
}
