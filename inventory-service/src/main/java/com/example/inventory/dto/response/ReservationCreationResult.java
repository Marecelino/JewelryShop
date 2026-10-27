package com.example.inventory.dto.response;

public record ReservationCreationResult(
        InventoryReservationResponse response,
        boolean newlyCreated
) {
}
