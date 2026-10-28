package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.request.OrderItemRequest;
import com.example.order.dto.response.CancelOrderResult;
import com.example.order.dto.response.CreateOrderResult;
import com.example.order.dto.response.OrderResponse;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.*;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private ObjectMapper objectMapper;
    private JwtAuthenticationToken userAuth;
    private JwtAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.addMixIn(org.springframework.data.domain.PageImpl.class, PageImplMixIn.class);

        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        Jwt userJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "10", "roles", List.of("USER"))
        );
        userAuth = new JwtAuthenticationToken(userJwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Jwt adminJwt = new Jwt(
                "token-value-admin",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "99", "roles", List.of("ADMIN"))
        );
        adminAuth = new JwtAuthenticationToken(adminJwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("POST /api/v1/orders valid creation -> 201 Created + Location")
    void createOrder_Success_Returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        OrderResponse response = new OrderResponse(
                1L, "ORD-20261027-ABC", 10L, OrderStatus.AWAITING_PAYMENT,
                new BigDecimal("5000.00"), "idem-1", Instant.now(), Instant.now(), List.of()
        );
        CreateOrderResult result = new CreateOrderResult(response, CreateOrderResult.Status.CREATED);

        when(orderService.createOrder(eq(10L), eq("idem-1"), any(CreateOrderRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/ORD-20261027-ABC")))
                .andExpect(jsonPath("$.orderReference").value("ORD-20261027-ABC"))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    @DisplayName("POST /api/v1/orders idempotent replay -> 200 OK")
    void createOrder_Existing_Returns200() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        OrderResponse response = new OrderResponse(
                1L, "ORD-20261027-ABC", 10L, OrderStatus.AWAITING_PAYMENT,
                new BigDecimal("5000.00"), "idem-1", Instant.now(), Instant.now(), List.of()
        );
        CreateOrderResult result = new CreateOrderResult(response, CreateOrderResult.Status.EXISTING);

        when(orderService.createOrder(eq(10L), eq("idem-1"), any(CreateOrderRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value("ORD-20261027-ABC"));
    }

    @Test
    @DisplayName("POST /api/v1/orders pending retry -> 202 Accepted + Location")
    void createOrder_Pending_Returns202() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        OrderResponse response = new OrderResponse(
                1L, "ORD-20261027-ABC", 10L, OrderStatus.PENDING_RESERVATION,
                new BigDecimal("5000.00"), "idem-1", Instant.now(), Instant.now(), List.of()
        );
        CreateOrderResult result = new CreateOrderResult(response, CreateOrderResult.Status.ACCEPTED);

        when(orderService.createOrder(eq(10L), eq("idem-1"), any(CreateOrderRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/ORD-20261027-ABC")))
                .andExpect(jsonPath("$.status").value("PENDING_RESERVATION"));
    }

    @Test
    @DisplayName("POST /api/v1/orders without Idempotency-Key -> 400 Bad Request")
    void createOrder_MissingIdempotencyKey_Returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));
    }

    @Test
    @DisplayName("POST /api/v1/orders with empty items -> 400 Bad Request")
    void createOrder_EmptyItems_Returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/orders with null item element in list -> 400 Bad Request")
    void createOrder_NullItemElement_Returns400() throws Exception {
        String payload = "{\"items\":[null]}";

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/orders exceeding max items limit -> 400 Bad Request")
    void createOrder_ExceedingLimit_Returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(1, 10),
                new OrderItemRequest(2, 10),
                new OrderItemRequest(3, 1)
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceeds maximum allowed limit")));
    }

    @Test
    @DisplayName("POST /api/v1/orders out-of-stock -> 409 Conflict")
    void createOrder_OutOfStock_Returns409() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        when(orderService.createOrder(eq(10L), eq("idem-1"), any()))
                .thenThrow(new ProductUnavailableException(1, "Item unavailable or out of stock"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("out of stock")));
    }

    @Test
    @DisplayName("POST /api/v1/orders downstream outage -> 503 Service Unavailable")
    void createOrder_DownstreamOutage_Returns503() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        when(orderService.createOrder(eq(10L), eq("idem-1"), any()))
                .thenThrow(new DownstreamServiceException("product-service", 503, "Unavailable"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{ref} success -> 200 OK")
    void getOrder_Success() throws Exception {
        OrderResponse response = new OrderResponse(
                1L, "ORD-123", 10L, OrderStatus.AWAITING_PAYMENT,
                new BigDecimal("1000.00"), "key", Instant.now(), Instant.now(), List.of()
        );
        when(orderService.getOrderByReference("ORD-123", 10L, false)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/ORD-123").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value("ORD-123"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{ref} not found -> 404 Not Found")
    void getOrder_NotFound() throws Exception {
        when(orderService.getOrderByReference("ORD-NOTFOUND", 10L, false))
                .thenThrow(new OrderNotFoundException("ORD-NOTFOUND"));

        mockMvc.perform(get("/api/v1/orders/ORD-NOTFOUND").principal(userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{ref} forbidden -> 403 Forbidden")
    void getOrder_Forbidden() throws Exception {
        when(orderService.getOrderByReference("ORD-OTHER", 10L, false))
                .thenThrow(new OrderAccessDeniedException("You do not have permission"));

        mockMvc.perform(get("/api/v1/orders/ORD-OTHER").principal(userAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /api/v1/orders valid pagination -> 200 OK")
    void listOrders_Success() throws Exception {
        when(orderService.listOrders(eq(10L), eq(false), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/orders?page=0&size=10&sort=createdAt,desc").principal(userAuth))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/orders with status filter -> 200 OK")
    void listOrders_WithStatusFilter_Success() throws Exception {
        when(orderService.listOrders(eq(10L), eq(false), eq(OrderStatus.AWAITING_PAYMENT), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/orders?page=0&size=10&status=AWAITING_PAYMENT").principal(userAuth))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/orders disallowed sort field -> 400 Bad Request")
    void listOrders_DisallowedSort_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders?sort=unknownField,asc").principal(userAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("not permitted")));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{ref}/cancellation full release -> 200 OK")
    void cancelOrder_FullyCancelled_Returns200() throws Exception {
        OrderResponse response = new OrderResponse(
                1L, "ORD-123", 10L, OrderStatus.CANCELLED,
                new BigDecimal("1000.00"), "key", Instant.now(), Instant.now(), List.of()
        );
        when(orderService.cancelOrder("ORD-123", 10L, false))
                .thenReturn(new CancelOrderResult(response, true));

        mockMvc.perform(post("/api/v1/orders/ORD-123/cancellation").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{ref}/cancellation pending release -> 202 Accepted")
    void cancelOrder_PendingRelease_Returns202() throws Exception {
        OrderResponse response = new OrderResponse(
                1L, "ORD-123", 10L, OrderStatus.CANCELLATION_PENDING,
                new BigDecimal("1000.00"), "key", Instant.now(), Instant.now(), List.of()
        );
        when(orderService.cancelOrder("ORD-123", 10L, false))
                .thenReturn(new CancelOrderResult(response, false));

        mockMvc.perform(post("/api/v1/orders/ORD-123/cancellation").principal(userAuth))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{ref}/cancellation conflict state -> 409 Conflict")
    void cancelOrder_Conflict_Returns409() throws Exception {
        when(orderService.cancelOrder("ORD-123", 10L, false))
                .thenThrow(new OrderConflictException("Cannot cancel order in status CONFIRMED"));

        mockMvc.perform(post("/api/v1/orders/ORD-123/cancellation").principal(userAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private abstract static class PageImplMixIn {
        @com.fasterxml.jackson.annotation.JsonIgnore
        public abstract org.springframework.data.domain.Pageable getPageable();
    }
}
