package com.example.inventory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalApiSecurityFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("When request is for public API, filter is bypassed")
    void doFilter_whenPublicPath_bypassesFilter() throws ServletException, IOException {
        InternalApiSecurityFilter filter = new InternalApiSecurityFilter("test-secret-key", objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory-items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("When internal request has valid key, request proceeds")
    void doFilter_whenValidKey_proceeds() throws ServletException, IOException {
        InternalApiSecurityFilter filter = new InternalApiSecurityFilter("valid-key-12345", objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/inventory/reservations");
        request.addHeader(InternalApiSecurityFilter.INTERNAL_KEY_HEADER, "valid-key-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("When internal request is missing key header, returns 401 Unauthorized")
    void doFilter_whenMissingKeyHeader_returns401() throws ServletException, IOException {
        InternalApiSecurityFilter filter = new InternalApiSecurityFilter("valid-key-12345", objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/inventory/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid internal service key");
    }

    @Test
    @DisplayName("When internal request provides invalid key, returns 401 Unauthorized")
    void doFilter_whenInvalidKey_returns401() throws ServletException, IOException {
        InternalApiSecurityFilter filter = new InternalApiSecurityFilter("valid-key-12345", objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/inventory/reservations");
        request.addHeader(InternalApiSecurityFilter.INTERNAL_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid internal service key");
    }

    @Test
    @DisplayName("When server key is unconfigured or empty, fails closed and returns 401")
    void doFilter_whenServerKeyEmpty_failsClosedWith401() throws ServletException, IOException {
        InternalApiSecurityFilter filter = new InternalApiSecurityFilter("", objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/inventory/reservations");
        request.addHeader(InternalApiSecurityFilter.INTERNAL_KEY_HEADER, "any-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
