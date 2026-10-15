package com.example.inventory.dto.response;

import com.example.inventory.enums.InventoryStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class InventoryItemResponse {

    private final Long inventoryItemId;
    private final Integer productId;
    private final String barcode;
    private final String serialNumber;
    private final BigDecimal actualWeightGrams;
    private final BigDecimal actualGoldPurityPercent;
    private final String certificateNumber;
    private final String locationCode;
    private final InventoryStatus status;
    private final String reservationReference;
    private final Instant reservedUntil;
    private final Instant createdAt;
    private final Instant updatedAt;

    public InventoryItemResponse(
            Long inventoryItemId,
            Integer productId,
            String barcode,
            String serialNumber,
            BigDecimal actualWeightGrams,
            BigDecimal actualGoldPurityPercent,
            String certificateNumber,
            String locationCode,
            InventoryStatus status,
            String reservationReference,
            Instant reservedUntil,
            Instant createdAt,
            Instant updatedAt) {

        this.inventoryItemId = inventoryItemId;
        this.productId = productId;
        this.barcode = barcode;
        this.serialNumber = serialNumber;
        this.actualWeightGrams = actualWeightGrams;
        this.actualGoldPurityPercent = actualGoldPurityPercent;
        this.certificateNumber = certificateNumber;
        this.locationCode = locationCode;
        this.status = status;
        this.reservationReference = reservationReference;
        this.reservedUntil = reservedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public BigDecimal getActualWeightGrams() {
        return actualWeightGrams;
    }

    public BigDecimal getActualGoldPurityPercent() {
        return actualGoldPurityPercent;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public InventoryStatus getStatus() {
        return status;
    }

    public String getReservationReference() {
        return reservationReference;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}