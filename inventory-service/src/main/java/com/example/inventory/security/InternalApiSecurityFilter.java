package com.example.inventory.security;

import com.example.inventory.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiSecurityFilter extends OncePerRequestFilter {

    public static final String INTERNAL_KEY_HEADER = "X-Internal-Service-Key";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final String configuredApiKey;
    private final ObjectMapper objectMapper;

    @Autowired
    public InternalApiSecurityFilter(
            @Value("${inventory.internal.auth.api-key:}") String configuredApiKey
    ) {
        this.configuredApiKey = configuredApiKey;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    // Constructor for testing with custom ObjectMapper
    public InternalApiSecurityFilter(String configuredApiKey, ObjectMapper objectMapper) {
        this.configuredApiKey = configuredApiKey;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only apply security check to internal endpoints; public APIs are completely unaffected
        return !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedKey = request.getHeader(INTERNAL_KEY_HEADER);

        if (!isValidKey(providedKey)) {
            sendUnauthorizedResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidKey(String providedKey) {
        // Fail-closed: If the server-side key is empty/unconfigured, deny all internal access
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return false;
        }
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void sendUnauthorizedResponse(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Missing or invalid internal service key",
                request.getRequestURI(),
                Map.of()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
