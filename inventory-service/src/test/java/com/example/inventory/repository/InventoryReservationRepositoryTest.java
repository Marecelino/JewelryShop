package com.example.inventory.repository;

import com.example.inventory.dto.response.InventoryReservationResponse;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.enums.InventoryStatus;
import com.example.inventory.exception.ReservationConflictException;
import com.example.inventory.service.InventoryReservationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InventoryReservationRepositoryTest {

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final List<Long> createdReservationIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdInventoryItemIds = new CopyOnWriteArrayList<>();

    private static final int TEST_PRODUCT_ID = 888999;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        // Clean up test data in reverse FK order without affecting baseline demo data
        transactionTemplate.execute(status -> {
            for (Long resId : createdReservationIds) {
                inventoryReservationRepository.deleteById(resId);
            }
            createdReservationIds.clear();

            for (Long itemId : createdInventoryItemIds) {
                inventoryItemRepository.deleteById(itemId);
            }
            createdInventoryItemIds.clear();
            return null;
        });

        // Verify baseline count is strictly preserved at 23
        long currentCount = inventoryItemRepository.count();
        assertThat(currentCount).isEqualTo(23L);
    }

    private InventoryItem createTestItem(Integer productId, String barcode, InventoryStatus status) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setBarcode(barcode);
        item.setActualWeightGrams(new BigDecimal("3.500"));
        item.setStatus(status);
        return item;
    }

    private InventoryReservation createTestReservation(
            String orderRef,
            String itemRef,
            Integer productId,
            Long itemId,
            InventoryReservationStatus status,
            Instant reservedAt,
            Instant expiresAt,
            Instant completedAt
    ) {
        InventoryReservation res = new InventoryReservation();
        res.setOrderReference(orderRef);
        res.setOrderItemReference(itemRef);
        res.setProductId(productId);
        res.setInventoryItemId(itemId);
        res.setStatus(status);
        res.setTtlMinutes(15);
        res.setReservedAt(reservedAt != null ? reservedAt.truncatedTo(ChronoUnit.MILLIS) : Instant.now().truncatedTo(ChronoUnit.MILLIS));
        res.setExpiresAt(expiresAt.truncatedTo(ChronoUnit.MILLIS));
        res.setCompletedAt(completedAt != null ? completedAt.truncatedTo(ChronoUnit.MILLIS) : null);
        return res;
    }

    private InventoryReservation createTestReservation(
            String orderRef,
            String itemRef,
            Integer productId,
            Long itemId,
            InventoryReservationStatus status,
            Instant expiresAt,
            Instant completedAt
    ) {
        return createTestReservation(orderRef, itemRef, productId, itemId, status, Instant.now(), expiresAt, completedAt);
    }

    @Test
    @DisplayName("Persist and retrieve reservation verifies mapping, generated PK and constraints")
    void persistAndRetrieveReservation_succeeds() {
        transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-001", InventoryStatus.AVAILABLE);
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());

            Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
            InventoryReservation reservation = createTestReservation(
                    "ORD-TEST-1", "ITEM-1", TEST_PRODUCT_ID, item.getId(),
                    InventoryReservationStatus.ACTIVE, expiresAt, null
            );
            inventoryReservationRepository.save(reservation);
            createdReservationIds.add(reservation.getId());

            Optional<InventoryReservation> found = inventoryReservationRepository.findById(reservation.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isNotNull();
            assertThat(found.get().getOrderReference()).isEqualTo("ORD-TEST-1");
            assertThat(found.get().getStatus()).isEqualTo(InventoryReservationStatus.ACTIVE);
            assertThat(found.get().getCompletedAt()).isNull();

            return null;
        });
    }

    @Test
    @DisplayName("Unique order line business identity rejects duplicate (orderReference, orderItemReference)")
    void uniqueOrderLineBusinessIdentity_rejectsDuplicate() {
        transactionTemplate.execute(status -> {
            InventoryItem item1 = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-002", InventoryStatus.AVAILABLE);
            InventoryItem item2 = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-003", InventoryStatus.AVAILABLE);
            entityManager.persist(item1);
            entityManager.persist(item2);
            entityManager.flush();
            createdInventoryItemIds.add(item1.getId());
            createdInventoryItemIds.add(item2.getId());

            InventoryReservation res1 = createTestReservation(
                    "ORD-DUP", "ITEM-SAME", TEST_PRODUCT_ID, item1.getId(),
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES), null
            );
            inventoryReservationRepository.saveAndFlush(res1);
            createdReservationIds.add(res1.getId());

            InventoryReservation res2 = createTestReservation(
                    "ORD-DUP", "ITEM-SAME", TEST_PRODUCT_ID, item2.getId(),
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES), null
            );

            assertThatThrownBy(() -> inventoryReservationRepository.saveAndFlush(res2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            status.setRollbackOnly();
            return null;
        });
    }

    @Test
    @DisplayName("Filtered unique index UX_inventory_reservations_active_item rejects two ACTIVE reservations for same item")
    void filteredUniqueActiveItemIndex_rejectsTwoActiveReservationsForSameItem() {
        transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-004", InventoryStatus.AVAILABLE);
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());

            // First ACTIVE reservation on item
            InventoryReservation res1 = createTestReservation(
                    "ORD-FLT-1", "ITEM-1", TEST_PRODUCT_ID, item.getId(),
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES), null
            );
            inventoryReservationRepository.saveAndFlush(res1);
            createdReservationIds.add(res1.getId());

            // Second ACTIVE reservation on same item -> MUST FAIL via filtered unique index
            InventoryReservation res2 = createTestReservation(
                    "ORD-FLT-2", "ITEM-2", TEST_PRODUCT_ID, item.getId(),
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES), null
            );

            assertThatThrownBy(() -> inventoryReservationRepository.saveAndFlush(res2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            status.setRollbackOnly();
            return null;
        });
    }

    @Test
    @DisplayName("Check constraint CK_inventory_reservations_completed_at enforces consistency with status")
    void completedAtConstraint_enforcesConsistency() {
        transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-005", InventoryStatus.AVAILABLE);
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());

            // Invalid case: ACTIVE status but completed_at is NOT NULL -> violates check constraint
            InventoryReservation invalidActive = createTestReservation(
                    "ORD-CK-1", "ITEM-1", TEST_PRODUCT_ID, item.getId(),
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES), Instant.now()
            );

            assertThatThrownBy(() -> inventoryReservationRepository.saveAndFlush(invalidActive))
                    .isInstanceOf(DataIntegrityViolationException.class);

            status.setRollbackOnly();
            return null;
        });
    }

    @Test
    @DisplayName("findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc returns only expired active reservations")
    void findExpiredReservations_returnsOnlyEligibleRecords() {
        transactionTemplate.execute(status -> {
            InventoryItem item1 = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-006", InventoryStatus.AVAILABLE);
            InventoryItem item2 = createTestItem(TEST_PRODUCT_ID, "TEST-RES-BC-007", InventoryStatus.AVAILABLE);
            entityManager.persist(item1);
            entityManager.persist(item2);
            entityManager.flush();
            createdInventoryItemIds.add(item1.getId());
            createdInventoryItemIds.add(item2.getId());

            Instant now = Instant.now();
            // res1: reserved 20 minutes ago, expired 5 minutes ago (expiresAt > reservedAt)
            InventoryReservation res1 = createTestReservation(
                    "ORD-EXP-1", "ITEM-1", TEST_PRODUCT_ID, item1.getId(),
                    InventoryReservationStatus.ACTIVE,
                    now.minus(20, ChronoUnit.MINUTES),
                    now.minus(5, ChronoUnit.MINUTES),
                    null
            );
            // res2: reserved now, expires 15 minutes in the future
            InventoryReservation res2 = createTestReservation(
                    "ORD-EXP-2", "ITEM-2", TEST_PRODUCT_ID, item2.getId(),
                    InventoryReservationStatus.ACTIVE,
                    now,
                    now.plus(15, ChronoUnit.MINUTES),
                    null
            );

            inventoryReservationRepository.save(res1);
            inventoryReservationRepository.save(res2);
            createdReservationIds.add(res1.getId());
            createdReservationIds.add(res2.getId());

            List<InventoryReservation> expired = inventoryReservationRepository
                    .findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                            InventoryReservationStatus.ACTIVE,
                            Instant.now()
                    );

            assertThat(expired).extracting(InventoryReservation::getId).contains(res1.getId());
            assertThat(expired).extracting(InventoryReservation::getId).doesNotContain(res2.getId());

            return null;
        });
    }

    @Test
    @DisplayName("Genuine concurrent locking test: 4 threads competing for 2 items never receive same physical item")
    void concurrentLockingTest_competingThreadsNeverGetSameItem() throws InterruptedException, ExecutionException {
        // Setup: Exactly 2 available physical items for product 777111
        final int concurrencyProductId = 777111;
        Long itemId1 = transactionTemplate.execute(status -> {
            InventoryItem i1 = createTestItem(concurrencyProductId, "TEST-CONC-001", InventoryStatus.AVAILABLE);
            entityManager.persist(i1);
            entityManager.flush();
            return i1.getId();
        });
        Long itemId2 = transactionTemplate.execute(status -> {
            InventoryItem i2 = createTestItem(concurrencyProductId, "TEST-CONC-002", InventoryStatus.AVAILABLE);
            entityManager.persist(i2);
            entityManager.flush();
            return i2.getId();
        });

        createdInventoryItemIds.add(itemId1);
        createdInventoryItemIds.add(itemId2);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Future<Optional<Long>>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger emptyCount = new AtomicInteger(0);
        Set<Long> reservedItemIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to start simultaneously
                    return transactionTemplate.execute(status -> {
                        Optional<Long> lockedIdOpt = inventoryItemRepository.findAvailableItemWithLock(concurrencyProductId);
                        if (lockedIdOpt.isPresent()) {
                            Long lockedId = lockedIdOpt.get();
                            InventoryItem item = inventoryItemRepository.findById(lockedId).orElseThrow();
                            item.setStatus(InventoryStatus.RESERVED);
                            item.setReservationReference("ORD-CONC-" + index);
                            item.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                            inventoryItemRepository.save(item);

                            InventoryReservation res = createTestReservation(
                                    "ORD-CONC-" + index, "ITEM-" + index, concurrencyProductId, lockedId,
                                    InventoryReservationStatus.ACTIVE, item.getReservedUntil(), null
                            );
                            inventoryReservationRepository.save(res);
                            createdReservationIds.add(res.getId());
                            return Optional.of(lockedId);
                        }
                        return Optional.empty();
                    });
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        for (Future<Optional<Long>> future : futures) {
            Optional<Long> result = future.get();
            if (result.isPresent()) {
                successCount.incrementAndGet();
                reservedItemIds.add(result.get());
            } else {
                emptyCount.incrementAndGet();
            }
        }

        // Proof of correctness:
        // 1. Exactly 2 threads succeeded because there were only 2 items
        assertThat(successCount.get()).isEqualTo(2);
        // 2. Exactly 2 threads got empty because stock was exhausted
        assertThat(emptyCount.get()).isEqualTo(2);
        // 3. The 2 successful threads got 2 DISTINCT physical items (NO DOUBLE-BOOKING!)
        assertThat(reservedItemIds).hasSize(2);
        assertThat(reservedItemIds).containsExactlyInAnyOrder(itemId1, itemId2);
    }

    @Test
    @DisplayName("Genuine SQL Server test: Expired commit delegates to isolated expiration, persists EXPIRED and releases item to AVAILABLE")
    void commitExpiredReservation_persistsExpiredStatusAndReleasesItem() {
        // 1. Create an expired reservation and its associated RESERVED inventory item
        Long itemId = transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-EXP-COMMIT-01", InventoryStatus.RESERVED);
            item.setReservationReference("ORD-EXP-COMMIT");
            item.setReservedUntil(Instant.now().minus(5, ChronoUnit.MINUTES));
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());
            return item.getId();
        });

        Long resId = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            InventoryReservation res = createTestReservation(
                    "ORD-EXP-COMMIT", "ITEM-1", TEST_PRODUCT_ID, itemId,
                    InventoryReservationStatus.ACTIVE,
                    now.minus(20, ChronoUnit.MINUTES),
                    now.minus(5, ChronoUnit.MINUTES),
                    null
            );
            inventoryReservationRepository.save(res);
            createdReservationIds.add(res.getId());
            return res.getId();
        });

        // 2 & 3. Call commit operation on the expired reservation -> Expect ReservationConflictException
        assertThatThrownBy(() -> inventoryReservationService.commitReservation(resId))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining("expired");

        // 4 & 5. Read records in a fresh transaction / persistence context
        transactionTemplate.execute(status -> {
            entityManager.clear();

            InventoryReservation updatedRes = inventoryReservationRepository.findById(resId).orElseThrow();
            assertThat(updatedRes.getStatus()).isEqualTo(InventoryReservationStatus.EXPIRED);
            assertThat(updatedRes.getCompletedAt()).isNotNull();

            InventoryItem updatedItem = inventoryItemRepository.findById(itemId).orElseThrow();
            assertThat(updatedItem.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            assertThat(updatedItem.getReservationReference()).isNull();
            assertThat(updatedItem.getReservedUntil()).isNull();

            return null;
        });
    }

    @Test
    @DisplayName("Genuine SQL Server test: Concurrent commit and release on same active reservation - one wins, other receives 409, consistent database state")
    void concurrentCommitAndRelease_oneWinsAndOtherFails() throws Exception {
        Long itemId = transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-RACE-01", InventoryStatus.RESERVED);
            item.setReservationReference("ORD-RACE-1");
            item.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());
            return item.getId();
        });

        Long resId = transactionTemplate.execute(status -> {
            InventoryReservation res = createTestReservation(
                    "ORD-RACE-1", "ITEM-1", TEST_PRODUCT_ID, itemId,
                    InventoryReservationStatus.ACTIVE,
                    Instant.now().plus(15, ChronoUnit.MINUTES),
                    null
            );
            inventoryReservationRepository.save(res);
            createdReservationIds.add(res.getId());
            return res.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        Future<?> commitFuture = executor.submit(() -> {
            try {
                startLatch.await();
                return inventoryReservationService.commitReservation(resId);
            } catch (Exception ex) {
                return ex;
            } finally {
                finishLatch.countDown();
            }
        });

        Future<?> releaseFuture = executor.submit(() -> {
            try {
                startLatch.await();
                return inventoryReservationService.releaseReservation(resId);
            } catch (Exception ex) {
                return ex;
            } finally {
                finishLatch.countDown();
            }
        });

        // Fire both operations simultaneously
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Object commitResult = commitFuture.get();
        Object releaseResult = releaseFuture.get();

        boolean commitSucceeded = commitResult instanceof InventoryReservationResponse;
        boolean releaseSucceeded = releaseResult instanceof InventoryReservationResponse;

        // Exactly one must succeed, the other must fail with conflict
        assertThat(commitSucceeded ^ releaseSucceeded).isTrue();

        // Fresh database read to verify consistency
        transactionTemplate.execute(status -> {
            entityManager.clear();
            InventoryReservation res = inventoryReservationRepository.findById(resId).orElseThrow();
            InventoryItem item = inventoryItemRepository.findById(itemId).orElseThrow();

            if (commitSucceeded) {
                assertThat(releaseResult).isInstanceOf(ReservationConflictException.class);
                assertThat(res.getStatus()).isEqualTo(InventoryReservationStatus.COMMITTED);
                assertThat(item.getStatus()).isEqualTo(InventoryStatus.SOLD);
            } else {
                assertThat(commitResult).isInstanceOf(ReservationConflictException.class);
                assertThat(res.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
                assertThat(item.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            }
            assertThat(item.getReservationReference()).isNull();
            assertThat(item.getReservedUntil()).isNull();
            return null;
        });
    }

    @Test
    @DisplayName("Genuine SQL Server test: Concurrent duplicate commit - both threads succeed idempotently and item remains SOLD")
    void concurrentDuplicateCommit_bothSucceedIdempotently() throws Exception {
        Long itemId = transactionTemplate.execute(status -> {
            InventoryItem item = createTestItem(TEST_PRODUCT_ID, "TEST-RACE-02", InventoryStatus.RESERVED);
            item.setReservationReference("ORD-RACE-2");
            item.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            entityManager.persist(item);
            entityManager.flush();
            createdInventoryItemIds.add(item.getId());
            return item.getId();
        });

        Long resId = transactionTemplate.execute(status -> {
            InventoryReservation res = createTestReservation(
                    "ORD-RACE-2", "ITEM-1", TEST_PRODUCT_ID, itemId,
                    InventoryReservationStatus.ACTIVE,
                    Instant.now().plus(15, ChronoUnit.MINUTES),
                    null
            );
            inventoryReservationRepository.save(res);
            createdReservationIds.add(res.getId());
            return res.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return inventoryReservationService.commitReservation(resId);
                } catch (Exception ex) {
                    return ex;
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        for (Future<Object> f : futures) {
            assertThat(f.get()).isInstanceOf(InventoryReservationResponse.class);
            InventoryReservationResponse resp = (InventoryReservationResponse) f.get();
            assertThat(resp.status()).isEqualTo(InventoryReservationStatus.COMMITTED);
        }

        transactionTemplate.execute(status -> {
            entityManager.clear();
            InventoryReservation res = inventoryReservationRepository.findById(resId).orElseThrow();
            InventoryItem item = inventoryItemRepository.findById(itemId).orElseThrow();
            assertThat(res.getStatus()).isEqualTo(InventoryReservationStatus.COMMITTED);
            assertThat(item.getStatus()).isEqualTo(InventoryStatus.SOLD);
            return null;
        });
    }
}
