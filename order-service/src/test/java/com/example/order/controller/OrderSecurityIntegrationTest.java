package com.example.order.controller;

import com.example.order.client.InventoryReservationClient;
import com.example.order.client.ProductClient;
import com.example.order.dto.response.OrderResponse;
import com.example.order.enums.OrderStatus;
import com.example.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class OrderSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private InventoryReservationClient inventoryReservationClient;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("1. Direct request without JWT -> HTTP 401 Unauthorized with ApiErrorResponse")
    void missingJwt_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("2. Direct request with invalid/malformed JWT -> HTTP 401 Unauthorized")
    void invalidJwt_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("3. JWT with unauthorized role (ROLE_GUEST) -> HTTP 403 Forbidden with ApiErrorResponse")
    void unauthorizedRole_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .with(jwt().jwt(builder -> builder.subject("10"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GUEST")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("4. JWT with ROLE_USER -> Access granted (HTTP 200)")
    void validUserRole_Returns200Ok() throws Exception {
        OrderResponse sampleOrder = new OrderResponse(
                1L, "ORD-USER-001", 10L, OrderStatus.AWAITING_PAYMENT,
                new BigDecimal("5000000.00"), "idem-user-1", Instant.now(), Instant.now(), List.of()
        );
        when(orderService.listOrders(eq(10L), eq(false), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleOrder)));

        mockMvc.perform(get("/api/v1/orders")
                        .with(jwt().jwt(builder -> builder.subject("10"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderReference").value("ORD-USER-001"));
    }

    @Test
    @DisplayName("5. JWT with ROLE_ADMIN -> Access granted (HTTP 200)")
    void validAdminRole_Returns200Ok() throws Exception {
        OrderResponse sampleOrder = new OrderResponse(
                2L, "ORD-ADMIN-001", 99L, OrderStatus.CONFIRMED,
                new BigDecimal("15000000.00"), "idem-admin-1", Instant.now(), Instant.now(), List.of()
        );
        when(orderService.listOrders(eq(99L), eq(true), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleOrder)));

        mockMvc.perform(get("/api/v1/orders")
                        .with(jwt().jwt(builder -> builder.subject("99"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderReference").value("ORD-ADMIN-001"));
    }

    @Test
    @DisplayName("6. Actuator health check permitAll -> HTTP 200 without JWT")
    void healthCheck_PermitAll_Returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Swagger OpenAPI docs permitAll -> HTTP 200 without JWT")
    void openApiDocs_PermitAll_Returns200() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
