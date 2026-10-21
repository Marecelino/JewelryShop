package com.example.product.dto.external;

/**
 * DTO đại diện cho một phần tử trong response của Inventory Service.
 *
 * <p>Endpoint được gọi:
 * {@code GET /api/v1/inventory-summaries?productIds=1&productIds=2}</p>
 *
 * <p>JSON response ví dụ:
 * <pre>
 * [
 *   {
 *     "productId": 1,
 *     "availableQuantity": 3,
 *     "reservedQuantity": 0,
 *     "soldQuantity": 0,
 *     "damagedQuantity": 0
 *   }
 * ]
 * </pre>
 * </p>
 *
 * <p>DTO này khớp với {@code InventorySummaryResponse} record bên Inventory Service.
 * Dùng record để JSON deserialization hoạt động, tên field phải khớp chính xác
 * (hoặc cần thêm {@code @JsonProperty} nếu tên khác).</p>
 */
public record InventorySummaryDto(
        Integer productId,
        long availableQuantity,
        long reservedQuantity,
        long soldQuantity,
        long damagedQuantity
) {
}
