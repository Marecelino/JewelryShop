package com.example.inventory.entity;

import com.example.inventory.enums.InventoryReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "inventory_reservations", schema = "dbo")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id", nullable = false)
    private Long id;

    @NotNull
    @Size(max = 100)
    @Column(name = "order_reference", nullable = false, length = 100)
    private String orderReference;

    @NotNull
    @Size(max = 100)
    @Column(name = "order_item_reference", nullable = false, length = 100)
    private String orderItemReference;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @NotNull
    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryReservationStatus status = InventoryReservationStatus.ACTIVE;

    @NotNull
    @Column(name = "ttl_minutes", nullable = false)
    private Integer ttlMinutes;

    @NotNull
    @Column(name = "reserved_at", nullable = false, columnDefinition = "datetime2(3)")
    private Instant reservedAt;

    @NotNull
    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime2(3)")
    private Instant expiresAt;

    @Column(name = "completed_at", columnDefinition = "datetime2(3)")
    private Instant completedAt;

    @NotNull
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(3)")
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(3)")
    private Instant updatedAt;

    public InventoryReservation() {
    }

    public InventoryReservation(
            String orderReference,
            String orderItemReference,
            Integer productId,
            Long inventoryItemId,
            Integer ttlMinutes,
            Instant reservedAt,
            Instant expiresAt
    ) {
        this.orderReference = orderReference;
        this.orderItemReference = orderItemReference;
        this.productId = productId;
        this.inventoryItemId = inventoryItemId;
        this.ttlMinutes = ttlMinutes;
        this.status = InventoryReservationStatus.ACTIVE;
        this.reservedAt = reservedAt != null ? reservedAt.truncatedTo(ChronoUnit.MILLIS) : Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.expiresAt = expiresAt != null ? expiresAt.truncatedTo(ChronoUnit.MILLIS) : this.reservedAt.plus(ttlMinutes, ChronoUnit.MINUTES);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (this.reservedAt == null) {
            this.reservedAt = now;
        }
        if (this.expiresAt == null && this.ttlMinutes != null) {
            this.expiresAt = this.reservedAt.plus(this.ttlMinutes, ChronoUnit.MINUTES);
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public void setInventoryItemId(Long inventoryItemId) {
        this.inventoryItemId = inventoryItemId;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryReservationStatus status) {
        this.status = status;
    }

    public Integer getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(Integer ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(Instant reservedAt) {
        this.reservedAt = reservedAt != null ? reservedAt.truncatedTo(ChronoUnit.MILLIS) : null;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt != null ? expiresAt.truncatedTo(ChronoUnit.MILLIS) : null;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt != null ? completedAt.truncatedTo(ChronoUnit.MILLIS) : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt.truncatedTo(ChronoUnit.MILLIS) : null;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt != null ? updatedAt.truncatedTo(ChronoUnit.MILLIS) : null;
    }
}
