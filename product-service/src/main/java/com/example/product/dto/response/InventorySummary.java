package com.example.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tổng hợp tồn kho của một sản phẩm, được lấy từ Inventory Service.
 *
 * <p>Object này được nhúng vào ProductResponse và chỉ xuất hiện khi
 * Inventory Service trả kết quả thành công. Không bao giờ dùng object này
 * để che giấu lỗi kết nối bằng cách điền số 0.</p>
 */
@Schema(description = "Inventory summary retrieved from Inventory Service")
public class InventorySummary {

    @Schema(description = "Number of items available for sale", example = "3")
    private final long availableQuantity;

    @Schema(description = "Number of items currently reserved", example = "1")
    private final long reservedQuantity;

    @Schema(description = "Number of items already sold", example = "5")
    private final long soldQuantity;

    @Schema(description = "Number of items marked as damaged", example = "0")
    private final long damagedQuantity;

    public InventorySummary(
            long availableQuantity,
            long reservedQuantity,
            long soldQuantity,
            long damagedQuantity) {

        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
        this.damagedQuantity = damagedQuantity;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getSoldQuantity() {
        return soldQuantity;
    }

    public long getDamagedQuantity() {
        return damagedQuantity;
    }
}
