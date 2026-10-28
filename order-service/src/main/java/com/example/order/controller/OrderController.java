package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CancelOrderResult;
import com.example.order.dto.response.CreateOrderResult;
import com.example.order.dto.response.OrderResponse;
import com.example.order.enums.OrderStatus;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Management", description = "APIs for creating, querying, and cancelling jewelry orders")
public class OrderController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "orderReference", "totalAmount", "status");
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    @Value("${app.order.max-items-per-order:20}")
    private int maxItemsPerOrder = 20;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create a new order with physical item reservations")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created and all items reserved"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of previously completed order"),
            @ApiResponse(responseCode = "202", description = "Order accepted and reservation workflow is pending retry"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid request payload"),
            @ApiResponse(responseCode = "404", description = "One or more products do not exist"),
            @ApiResponse(responseCode = "409", description = "Product out of stock or conflicting idempotency payload"),
            @ApiResponse(responseCode = "503", description = "Downstream service unavailable")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Idempotency key to safely retry requests", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required and cannot be blank");
        }
        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key cannot exceed 100 characters");
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("items cannot be empty");
        }

        if (request.getItems().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("items list cannot contain null elements");
        }

        int totalUnits = request.calculateTotalQuantity();
        if (totalUnits > maxItemsPerOrder) {
            throw new IllegalArgumentException(
                    "Total physical items (" + totalUnits + ") exceeds maximum allowed limit of " + maxItemsPerOrder
            );
        }

        Long userId = extractUserId(authentication);
        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey.trim(), request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{orderReference}")
                .buildAndExpand(result.order().orderReference())
                .toUri();

        if (result.isCreated()) {
            return ResponseEntity.created(location).body(result.order());
        }
        if (result.isAccepted()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).location(location).body(result.order());
        }
        return ResponseEntity.ok(result.order());
    }

    @GetMapping("/{orderReference}")
    @Operation(summary = "Get order details by reference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User does not own this order"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrderByReference(
            @PathVariable String orderReference,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");

        OrderResponse response = orderService.getOrderByReference(orderReference, userId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List orders with pagination, sort allowlist, and status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid page, size, or sort parameter")
    })
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) OrderStatus status,
            Authentication authentication
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Sort sortOrder = parseSortParameter(sort);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Long userId = extractUserId(authentication);
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");

        Page<OrderResponse> result = orderService.listOrders(userId, isAdmin, status, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderReference}/cancellation")
    @Operation(summary = "Cancel an order and release reserved inventory items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order fully cancelled and reservations released"),
            @ApiResponse(responseCode = "202", description = "Cancellation initiated but some reservations are pending retry"),
            @ApiResponse(responseCode = "403", description = "User does not own this order"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in its current status")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderReference,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");

        CancelOrderResult result = orderService.cancelOrder(orderReference, userId, isAdmin);
        if (result.fullyCancelled()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result.order());
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String subject = jwt.getSubject();
            try {
                return Long.parseLong(subject);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid user identifier in authentication token: " + subject);
            }
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid user identifier in authentication token: " + jwt.getSubject());
            }
        }
        throw new IllegalStateException("Authenticated principal is not a JWT token");
    }

    private boolean hasRole(Authentication authentication, String roleName) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (roleName.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private Sort parseSortParameter(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "orderId"));
        }

        String[] parts = sortParam.split(",");
        String property = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            throw new IllegalArgumentException("Sort by field '" + property + "' is not permitted. Allowed fields: " + ALLOWED_SORT_FIELDS);
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.DESC;
        }

        return Sort.by(direction, property).and(Sort.by(direction, "orderId"));
    }
}
