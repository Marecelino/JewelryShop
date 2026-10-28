package com.example.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Order item request representing product and quantity")
public class OrderItemRequest {

    @Schema(description = "ID of the product to order", example = "1")
    @NotNull(message = "productId is required")
    @Positive(message = "productId must be a positive integer")
    private Integer productId;

    @Schema(description = "Quantity of physical items", example = "1")
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 10, message = "quantity cannot exceed 10")
    private Integer quantity;

    public OrderItemRequest() {
    }

    public OrderItemRequest(Integer productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
