package com.example.inventory.repository;

import com.example.inventory.entity.InventoryItem;
import com.example.inventory.enums.InventoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback
class InventoryItemRepositoryTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private EntityManager entityManager;

    private InventoryItem createTestItem(
            Integer productId,
            String barcode,
            String serialNumber,
            InventoryStatus status
    ) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setBarcode(barcode);
        item.setSerialNumber(serialNumber);
        item.setActualWeightGrams(new BigDecimal("3.500"));
        item.setActualGoldPurityPercent(new BigDecimal("75.00"));
        item.setCertificateNumber("CERT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        item.setLocationCode("SAFE-TEST");
        item.setStatus(status);

        if (status == InventoryStatus.RESERVED) {
            item.setReservationReference("RES-REF-" + UUID.randomUUID().toString().substring(0, 8));
            item.setReservedUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        } else {
            item.setReservationReference(null);
            item.setReservedUntil(null);
        }

        return item;
    }

    @Test
    @DisplayName("existsByBarcode returns true for existing barcode and false for missing barcode")
    void existsByBarcode_whenBarcodeExistsAndMissing_returnsExpectedBoolean() {
        String existingBarcode = "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12);
        String missingBarcode = "TEST-BC-NONEXISTENT-" + UUID.randomUUID().toString().substring(0, 8);

        InventoryItem item = createTestItem(999001, existingBarcode, null, InventoryStatus.AVAILABLE);
        entityManager.persist(item);
        entityManager.flush();

        assertThat(inventoryItemRepository.existsByBarcode(existingBarcode)).isTrue();
        assertThat(inventoryItemRepository.existsByBarcode(missingBarcode)).isFalse();
    }

    @Test
    @DisplayName("existsBySerialNumber returns true for existing serial number and false for missing one")
    void existsBySerialNumber_whenSerialExistsAndMissing_returnsExpectedBoolean() {
        String existingSerial = "TEST-SN-" + UUID.randomUUID().toString().substring(0, 12);
        String missingSerial = "TEST-SN-NONEXISTENT-" + UUID.randomUUID().toString().substring(0, 8);

        InventoryItem item = createTestItem(
                999002,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                existingSerial,
                InventoryStatus.AVAILABLE
        );
        entityManager.persist(item);
        entityManager.flush();

        assertThat(inventoryItemRepository.existsBySerialNumber(existingSerial)).isTrue();
        assertThat(inventoryItemRepository.existsBySerialNumber(missingSerial)).isFalse();
    }

    @Test
    @DisplayName("findAll with Pageable supports pagination and sorting")
    void findAll_withPageable_supportsPaginationAndSorting() {
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id"));
        Page<InventoryItem> page = inventoryItemRepository.findAll(pageable);

        assertThat(page).isNotNull();
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getPageable().getPageNumber()).isZero();
        assertThat(page.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    @DisplayName("findById returns existing item with persisted fields")
    void findById_whenItemExists_returnsPersistedItem() {
        String barcode = "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12);
        InventoryItem item = createTestItem(999003, barcode, "SN-999003", InventoryStatus.AVAILABLE);
        entityManager.persist(item);
        entityManager.flush();
        Long generatedId = item.getId();

        Optional<InventoryItem> found = inventoryItemRepository.findById(generatedId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(generatedId);
        assertThat(found.get().getProductId()).isEqualTo(999003);
        assertThat(found.get().getBarcode()).isEqualTo(barcode);
        assertThat(found.get().getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
    }

    @Test
    @DisplayName("summarizeByProductIds calculates available, reserved, sold, and damaged counts correctly")
    void summarizeByProductIds_calculatesAllStatusCountsCorrectly() {
        Integer testProductId = 999100;

        InventoryItem availableItem = createTestItem(
                testProductId,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.AVAILABLE
        );
        InventoryItem reservedItem = createTestItem(
                testProductId,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.RESERVED
        );
        InventoryItem soldItem = createTestItem(
                testProductId,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.SOLD
        );
        InventoryItem damagedItem = createTestItem(
                testProductId,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.DAMAGED
        );

        entityManager.persist(availableItem);
        entityManager.persist(reservedItem);
        entityManager.persist(soldItem);
        entityManager.persist(damagedItem);
        entityManager.flush();

        List<InventorySummaryProjection> summaries = inventoryItemRepository.summarizeByProductIds(
                List.of(testProductId),
                InventoryStatus.AVAILABLE,
                InventoryStatus.RESERVED,
                InventoryStatus.SOLD,
                InventoryStatus.DAMAGED
        );

        assertThat(summaries).hasSize(1);
        InventorySummaryProjection summary = summaries.getFirst();
        assertThat(summary.getProductId()).isEqualTo(testProductId);
        assertThat(summary.getAvailableQuantity()).isEqualTo(1L);
        assertThat(summary.getReservedQuantity()).isEqualTo(1L);
        assertThat(summary.getSoldQuantity()).isEqualTo(1L);
        assertThat(summary.getDamagedQuantity()).isEqualTo(1L);
    }

    @Test
    @DisplayName("summarizeByProductIds filters strictly by the requested product IDs")
    void summarizeByProductIds_filtersStrictlyByRequestedProductIds() {
        Integer targetProduct = 999201;
        Integer otherProduct = 999202;

        InventoryItem targetItem = createTestItem(
                targetProduct,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.AVAILABLE
        );
        InventoryItem otherItem = createTestItem(
                otherProduct,
                "TEST-BC-" + UUID.randomUUID().toString().substring(0, 12),
                null,
                InventoryStatus.AVAILABLE
        );

        entityManager.persist(targetItem);
        entityManager.persist(otherItem);
        entityManager.flush();

        List<InventorySummaryProjection> summaries = inventoryItemRepository.summarizeByProductIds(
                List.of(targetProduct),
                InventoryStatus.AVAILABLE,
                InventoryStatus.RESERVED,
                InventoryStatus.SOLD,
                InventoryStatus.DAMAGED
        );

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().getProductId()).isEqualTo(targetProduct);
        assertThat(summaries.getFirst().getAvailableQuantity()).isEqualTo(1L);
    }

    @Test
    @DisplayName("summarizeByProductIds returns empty list for product with no inventory records")
    void summarizeByProductIds_whenProductHasNoInventoryRecords_returnsEmptyList() {
        Integer nonExistentProductId = 999999;

        List<InventorySummaryProjection> summaries = inventoryItemRepository.summarizeByProductIds(
                List.of(nonExistentProductId),
                InventoryStatus.AVAILABLE,
                InventoryStatus.RESERVED,
                InventoryStatus.SOLD,
                InventoryStatus.DAMAGED
        );

        assertThat(summaries).isEmpty();
    }
}
