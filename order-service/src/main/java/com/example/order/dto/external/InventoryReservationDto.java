package com.example.order.dto.external;

import java.time.Instant;

public record InventoryReservationDto(
        Long reservationId,
        String orderReference,
        String orderItemReference,
        Integer productId,
        Long inventoryItemId,
        String status,
        Integer ttlMinutes,
        Instant reservedAt,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isExpired() {
        return "EXPIRED".equalsIgnoreCase(status);
    }

    public boolean isReleased() {
        return "RELEASED".equalsIgnoreCase(status);
    }

    public boolean isCommitted() {
        return "COMMITTED".equalsIgnoreCase(status);
    }
}
