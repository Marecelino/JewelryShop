package com.example.order.dto.response;

import com.example.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Order details response")
public record OrderResponse(
        @Schema(description = "Primary order identifier", example = "10")
        Long orderId,

        @Schema(description = "Business order reference", example = "ORD-20261027-123456")
        String orderReference,

        @Schema(description = "Authenticated user ID who placed the order", example = "1")
        Long userId,

        @Schema(description = "Overall order status", example = "AWAITING_PAYMENT")
        OrderStatus status,

        @Schema(description = "Total order amount in VND", example = "11000000.00")
        BigDecimal totalAmount,

        @Schema(description = "Client-supplied idempotency key", example = "idem-key-12345")
        String idempotencyKey,

        @Schema(description = "Order creation timestamp")
        Instant createdAt,

        @Schema(description = "Order last update timestamp")
        Instant updatedAt,

        @Schema(description = "List of physical items belonging to this order")
        List<OrderItemResponse> items
) {
}
