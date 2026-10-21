package com.example.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product details with optional inventory summary")
public class ProductResponse {

    @Schema(description = "Unique product identifier", example = "1")
    private Integer productId;

    @Schema(description = "Product name", example = "Classic Gold Ring")
    private String productName;

    @Schema(description = "Product category", example = "Rings")
    private String category;

    @Schema(description = "Primary material", example = "18K Gold")
    private String material;

    @Schema(description = "Gemstone if any", example = "Diamond")
    private String gemstone;

    @Schema(description = "Price in VND", example = "5500000")
    private BigDecimal price;

    @Schema(description = "Product description")
    private String description;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Whether the product is active", example = "true")
    private Boolean active;

    /**
     * Thông tin tồn kho được lấy từ Inventory Service.
     *
     * <p>Trường này sẽ là {@code null} khi:
     * <ul>
     *   <li>Inventory Service không phản hồi hoặc trả lỗi</li>
     *   <li>Endpoint chỉ yêu cầu dữ liệu catalog (không cần tồn kho)</li>
     * </ul>
     * Giá trị {@code null} có nghĩa là "không lấy được thông tin tồn kho",
     * khác hoàn toàn với "không có hàng".</p>
     */
    @Schema(
            description = "Inventory summary from Inventory Service. Null means data unavailable, not zero stock.",
            nullable = true
    )
    private InventorySummary inventory;

    public ProductResponse() {
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getGemstone() {
        return gemstone;
    }

    public void setGemstone(String gemstone) {
        this.gemstone = gemstone;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public InventorySummary getInventory() {
        return inventory;
    }

    public void setInventory(InventorySummary inventory) {
        this.inventory = inventory;
    }
}