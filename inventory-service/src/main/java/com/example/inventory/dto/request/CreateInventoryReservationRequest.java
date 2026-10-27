package com.example.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateInventoryReservationRequest {

    @NotBlank(message = "orderReference is required")
    @Size(max = 100, message = "orderReference cannot exceed 100 characters")
    private String orderReference;

    @NotBlank(message = "orderItemReference is required")
    @Size(max = 100, message = "orderItemReference cannot exceed 100 characters")
    private String orderItemReference;

    @NotNull(message = "productId is required")
    @Positive(message = "productId must be a positive integer")
    private Integer productId;

    @NotNull(message = "ttlMinutes is required")
    @Min(value = 1, message = "ttlMinutes must be at least 1 minute")
    @Max(value = 1440, message = "ttlMinutes cannot exceed 1440 minutes (24 hours)")
    private Integer ttlMinutes = 15;

    public CreateInventoryReservationRequest() {
    }

    public CreateInventoryReservationRequest(
            String orderReference,
            String orderItemReference,
            Integer productId,
            Integer ttlMinutes
    ) {
        this.orderReference = orderReference;
        this.orderItemReference = orderItemReference;
        this.productId = productId;
        this.ttlMinutes = ttlMinutes;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public String getOrderItemReference() {
        return orderItemReference;
    }

    public void setOrderItemReference(String orderItemReference) {
        this.orderItemReference = orderItemReference;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(Integer ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }
}
