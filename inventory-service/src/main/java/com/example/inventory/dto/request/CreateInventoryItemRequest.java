package com.example.inventory.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateInventoryItemRequest {

    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be greater than 0")
    private Integer productId;

    @NotBlank(message = "Barcode is required")
    @Size(max = 50, message = "Barcode must not exceed 50 characters")
    private String barcode;

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @DecimalMin(value = "0.001", message = "Weight must be greater than 0")
    @DecimalMax(value = "1000.000", message = "Weight is unrealistically large")
    @Digits(integer = 4, fraction = 3)
    private BigDecimal actualWeightGrams;

    @DecimalMin(value = "37.50", message = "Gold purity must be at least 37.50% (9K)")
    @DecimalMax(value = "99.99", message = "Gold purity cannot exceed 99.99%")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal actualGoldPurityPercent;

    @Size(max = 100, message = "Certificate number must not exceed 100 characters")
    private String certificateNumber;

    @Size(max = 50, message = "Location code must not exceed 50 characters")
    private String locationCode;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public BigDecimal getActualWeightGrams() {
        return actualWeightGrams;
    }

    public void setActualWeightGrams(BigDecimal actualWeightGrams) {
        this.actualWeightGrams = actualWeightGrams;
    }

    public BigDecimal getActualGoldPurityPercent() {
        return actualGoldPurityPercent;
    }

    public void setActualGoldPurityPercent(BigDecimal actualGoldPurityPercent) {
        this.actualGoldPurityPercent = actualGoldPurityPercent;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public void setCertificateNumber(String certificateNumber) {
        this.certificateNumber = certificateNumber;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }
}
