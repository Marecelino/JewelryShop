package com.example.order.dto.response;

import com.example.order.enums.OrderItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Order item details response")
public record OrderItemResponse(
        @Schema(description = "Primary identifier of order item", example = "101")
        Long orderItemId,

        @Schema(description = "Unique item reference", example = "ITEM-ORD-ABC12345-1-1")
        String orderItemReference,

        @Schema(description = "Product identifier", example = "1")
        Integer productId,

        @Schema(description = "Snapshot product name at ordering", example = "Classic Gold Ring")
        String productNameSnapshot,

        @Schema(description = "Snapshot unit price in VND", example = "5500000.00")
        BigDecimal unitPrice,

        @Schema(description = "Inventory reservation identifier if reserved", example = "501")
        Long reservationId,

        @Schema(description = "Physical inventory item identifier if reserved", example = "2001")
        Long inventoryItemId,

        @Schema(description = "Current order item status", example = "RESERVED")
        OrderItemStatus status,

        @Schema(description = "Retry attempts made for reservation", example = "0")
        Integer retryCount,

        @Schema(description = "Item creation timestamp")
        Instant createdAt,

        @Schema(description = "Item last update timestamp")
        Instant updatedAt
) {
}
