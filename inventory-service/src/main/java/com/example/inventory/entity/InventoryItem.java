package com.example.inventory.entity;

import com.example.inventory.enums.InventoryStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_items" , schema = "dbo", indexes = {
    // ⚡ Bolt: Adding indexes for high-frequency lookup queries to improve search performance.
    // Impact: Avoids full table scans during inventory item lookups and aggregations. Expected to significantly reduce query time (e.g. from O(n) to O(log n)) when the table grows large.
    @Index(name = "idx_inventory_item_product_id", columnList = "product_id"),
    @Index(name = "idx_inventory_item_barcode", columnList = "barcode")
})
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_item_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Size(max = 50)
    @NotNull
    @Column(name = "barcode", nullable = false, length = 50)
    private String barcode;

    @Size(max = 100)
    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "actual_weight_grams", precision = 10, scale = 3)
    private BigDecimal actualWeightGrams;

    @Column(name = "actual_gold_purity_percent", precision = 5, scale = 2)
    private BigDecimal actualGoldPurityPercent;

    @Size(max = 100)
    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Size(max = 50)
    @Column(name = "location_code", length = 50)
    private String locationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryStatus status = InventoryStatus.AVAILABLE;

    @Size(max = 100)
    @Column(name = "reservation_reference", length = 100)
    private String reservationReference;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @NotNull
    @Column(name = "created_at", nullable = false,
            columnDefinition = "datetime2(3)")
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "datetime2(3)")
    private Instant updatedAt;

    //@Column(name = "row_version", columnDefinition = "timestamp not null")
    //private Object rowVersion;
    @Column(
            name = "row_version",
            columnDefinition = "rowversion",
            insertable = false,
            updatable = false
    )
    private byte[] rowVersion;

    public byte[] getRowVersion() {
        return rowVersion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public InventoryStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryStatus status) {
        this.status = status;
    }

    public String getReservationReference() {
        return reservationReference;
    }

    public void setReservationReference(String reservationReference) {
        this.reservationReference = reservationReference;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(Instant reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

   /* public Object getRowVersion() {
        return rowVersion;
    }*/

/*
    public void setRowVersion(Object rowVersion) {
        this.rowVersion = rowVersion;
    }
*/

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}