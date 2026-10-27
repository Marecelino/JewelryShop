package com.example.gateway.config;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.HttpBasicServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerHttpBasicAuthenticationConverter;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Custom WebFilter thay thế cho AuthenticationWebFilter mặc định của Spring Security.
 * Cho phép dễ dàng đặt Breakpoint, xem log chi tiết từng bước của quá trình xác thực
 * (extract token -> authenticate -> success/failure), và mở rộng các logic custom sau này.
 */
public class CustomAuthenticationWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationWebFilter.class);

    private final ReactiveAuthenticationManagerResolver<ServerWebExchange> authenticationManagerResolver;

    private ServerAuthenticationSuccessHandler authenticationSuccessHandler =
            new WebFilterChainServerAuthenticationSuccessHandler();

    private ServerAuthenticationConverter authenticationConverter =
            new ServerHttpBasicAuthenticationConverter();

    private ServerAuthenticationFailureHandler authenticationFailureHandler =
            new ServerAuthenticationEntryPointFailureHandler(new HttpBasicServerAuthenticationEntryPoint());

    private ServerSecurityContextRepository securityContextRepository =
            NoOpServerSecurityContextRepository.getInstance();

    private ServerWebExchangeMatcher requiresAuthenticationMatcher =
            ServerWebExchangeMatchers.anyExchange();

    public CustomAuthenticationWebFilter(ReactiveAuthenticationManager authenticationManager) {
        Assert.notNull(authenticationManager, "authenticationManager cannot be null");
        this.authenticationManagerResolver = (request) -> Mono.just(authenticationManager);
    }

    public CustomAuthenticationWebFilter(
            ReactiveAuthenticationManagerResolver<ServerWebExchange> authenticationManagerResolver) {
        Assert.notNull(authenticationManagerResolver, "authenticationManagerResolver cannot be null");
        this.authenticationManagerResolver = authenticationManagerResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.debug("--> [CustomAuthenticationWebFilter] Checking request [{} {}]",
                request.getMethod(), request.getURI().getPath());

        return this.requiresAuthenticationMatcher.matches(exchange)
                .filter(matchResult -> {
                    boolean isMatch = matchResult.isMatch();
                    if (!isMatch) {
                        log.debug("[CustomAuthenticationWebFilter] Request [{} {}] does not require authentication matcher",
                                request.getMethod(), request.getURI().getPath());
                    }
                    return isMatch;
                })
                .flatMap(matchResult -> {
                    log.debug("[CustomAuthenticationWebFilter] Extracting token via authenticationConverter for [{} {}]",
                            request.getMethod(), request.getURI().getPath());
                    return this.authenticationConverter.convert(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("<-- [CustomAuthenticationWebFilter] No token found for [{} {}]. Continuing chain without authentication",
                            request.getMethod(), request.getURI().getPath());
                    return chain.filter(exchange).then(Mono.empty());
                }))
                .flatMap(token -> authenticate(exchange, chain, token))
                .onErrorResume(AuthenticationException.class, ex -> {
                    log.warn("xx> [CustomAuthenticationWebFilter] AuthenticationException for [{} {}]: {}",
                            request.getMethod(), request.getURI().getPath(), ex.getMessage());
                    return this.authenticationFailureHandler
                            .onAuthenticationFailure(new WebFilterExchange(exchange, chain), ex);
                });
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, Authentication token) {
        ServerHttpRequest request = exchange.getRequest();
        log.debug("[CustomAuthenticationWebFilter] Authenticating token [{}] for [{} {}]",
                token.getClass().getSimpleName(), request.getMethod(), request.getURI().getPath());

        return this.authenticationManagerResolver.resolve(exchange)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("[CustomAuthenticationWebFilter] No ReactiveAuthenticationManager resolved for [{} {}]",
                            request.getMethod(), request.getURI().getPath());
                    return Mono.error(new IllegalStateException("No ReactiveAuthenticationManager resolved for " + token.getClass()));
                }))
                .flatMap(authenticationManager -> {
                    log.debug("[CustomAuthenticationWebFilter] Calling AuthenticationManager [{}]...",
                            authenticationManager.getClass().getSimpleName());
                    return authenticationManager.authenticate(token);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("[CustomAuthenticationWebFilter] No provider found for authentication token type: [{}]",
                            token.getClass());
                    return Mono.error(new IllegalStateException("No provider found for " + token.getClass()));
                }))
                .flatMap(authentication -> {
                    log.debug("==> [CustomAuthenticationWebFilter] Authentication succeeded for request [{} {}]",
                            request.getMethod(), request.getURI().getPath());
                    return onAuthenticationSuccess(authentication, new WebFilterExchange(exchange, chain));
                });
    }

    protected Mono<Void> onAuthenticationSuccess(Authentication authentication, WebFilterExchange webFilterExchange) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        log.debug("[CustomAuthenticationWebFilter] Storing authenticated principal [{}] in ReactiveSecurityContext",
                authentication.getName());

        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);

        return this.securityContextRepository.save(exchange, securityContext)
                .then(this.authenticationSuccessHandler.onAuthenticationSuccess(webFilterExchange, authentication))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
    }

    public void setSecurityContextRepository(ServerSecurityContextRepository securityContextRepository) {
        Assert.notNull(securityContextRepository, "securityContextRepository cannot be null");
        this.securityContextRepository = securityContextRepository;
    }

    public void setAuthenticationSuccessHandler(ServerAuthenticationSuccessHandler authenticationSuccessHandler) {
        Assert.notNull(authenticationSuccessHandler, "authenticationSuccessHandler cannot be null");
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    public void setAuthenticationConverter(Function<ServerWebExchange, Mono<Authentication>> authenticationConverter) {
        Assert.notNull(authenticationConverter, "authenticationConverter cannot be null");
        setServerAuthenticationConverter(authenticationConverter::apply);
    }

    public void setServerAuthenticationConverter(ServerAuthenticationConverter authenticationConverter) {
        Assert.notNull(authenticationConverter, "authenticationConverter cannot be null");
        this.authenticationConverter = authenticationConverter;
    }

    public void setAuthenticationFailureHandler(ServerAuthenticationFailureHandler authenticationFailureHandler) {
        Assert.notNull(authenticationFailureHandler, "authenticationFailureHandler cannot be null");
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    public void setRequiresAuthenticationMatcher(ServerWebExchangeMatcher requiresAuthenticationMatcher) {
        Assert.notNull(requiresAuthenticationMatcher, "requiresAuthenticationMatcher cannot be null");
        this.requiresAuthenticationMatcher = requiresAuthenticationMatcher;
    }
}
