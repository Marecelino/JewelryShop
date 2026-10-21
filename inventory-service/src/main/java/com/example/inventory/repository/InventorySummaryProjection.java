
package com.example.inventory.repository;

public interface InventorySummaryProjection {

    Integer getProductId();

    Long getAvailableQuantity();

    Long getReservedQuantity();

    Long getSoldQuantity();

    Long getDamagedQuantity();
}
