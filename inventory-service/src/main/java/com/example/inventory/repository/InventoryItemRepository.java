package com.example.inventory.repository;

import com.example.inventory.entity.InventoryItem;
import com.example.inventory.enums.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, Long> {

    boolean existsByBarcode(String barcode);

    boolean existsBySerialNumber(String serialNumber);

    @Query("""
        SELECT
            i.productId AS productId,
            SUM(CASE WHEN i.status = :available
                THEN 1L ELSE 0L END) AS availableQuantity,
            SUM(CASE WHEN i.status = :reserved
                THEN 1L ELSE 0L END) AS reservedQuantity,
            SUM(CASE WHEN i.status = :sold
                THEN 1L ELSE 0L END) AS soldQuantity,
            SUM(CASE WHEN i.status = :damaged
                THEN 1L ELSE 0L END) AS damagedQuantity
        FROM InventoryItem i
        WHERE i.productId IN :productIds
        GROUP BY i.productId
        """)
    List<InventorySummaryProjection> summarizeByProductIds(
            @Param("productIds") Collection<Integer> productIds,
            @Param("available") InventoryStatus available,
            @Param("reserved") InventoryStatus reserved,
            @Param("sold") InventoryStatus sold,
            @Param("damaged") InventoryStatus damaged
    );
}