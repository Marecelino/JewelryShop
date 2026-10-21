
package com.example.inventory.dto.response;

public record InventorySummaryResponse(
        Integer productId,
        long availableQuantity,
        long reservedQuantity,
        long soldQuantity,
        long damagedQuantity
) {
}
