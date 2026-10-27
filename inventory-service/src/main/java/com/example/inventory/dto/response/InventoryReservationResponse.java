package com.example.inventory.dto.response;

import com.example.inventory.enums.InventoryReservationStatus;

import java.time.Instant;

public record InventoryReservationResponse(
        Long reservationId,
        String orderReference,
        String orderItemReference,
        Integer productId,
        Long inventoryItemId,
        InventoryReservationStatus status,
        Integer ttlMinutes,
        Instant reservedAt,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
