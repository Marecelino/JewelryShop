package com.example.inventory.service;

import com.example.inventory.dto.request.CreateInventoryReservationRequest;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.enums.InventoryStatus;
import com.example.inventory.exception.ReservationConflictException;
import com.example.inventory.repository.InventoryItemRepository;
import com.example.inventory.repository.InventoryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class ReservationStateService {

    private static final Logger log = LoggerFactory.getLogger(ReservationStateService.class);

    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public ReservationStateService(
            InventoryReservationRepository inventoryReservationRepository,
            InventoryItemRepository inventoryItemRepository
    ) {
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Executes the critical reservation creation in an isolated transaction.
     * Selects an available item using SQL Server lock hints, transitions it to RESERVED,
     * and inserts the new reservation. If a concurrent duplicate occurs, only this transaction
     * rolls back, leaving no orphaned reserved items and allowing the caller to safely recover.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InventoryReservation createReservationTransaction(CreateInventoryReservationRequest request) {
        Long itemId = inventoryItemRepository.findAvailableItemWithLock(request.getProductId())
                .orElseThrow(() -> new ReservationConflictException(
                        "No available inventory item for product ID: " + request.getProductId()));

        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ReservationConflictException(
                        "Selected inventory item not found: " + itemId));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(request.getTtlMinutes(), ChronoUnit.MINUTES);

        item.setStatus(InventoryStatus.RESERVED);
        item.setReservationReference(request.getOrderReference());
        item.setReservedUntil(expiresAt);
        inventoryItemRepository.save(item);

        InventoryReservation reservation = new InventoryReservation(
                request.getOrderReference(),
                request.getOrderItemReference(),
                request.getProductId(),
                item.getId(),
                request.getTtlMinutes(),
                now,
                expiresAt
        );

        return inventoryReservationRepository.saveAndFlush(reservation);
    }

    /**
     * Executes expiration and stock release in an isolated new transaction.
     * Ensures that when called from an expired commit attempt or background scheduler,
     * the expiration and stock release are committed immediately and will not be rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<InventoryReservation> expireAndRelease(Long reservationId) {
        Optional<InventoryReservation> resOpt = inventoryReservationRepository.findByIdForUpdate(reservationId);
        if (resOpt.isEmpty()) {
            return Optional.empty();
        }

        InventoryReservation reservation = resOpt.get();
        if (reservation.getStatus() != InventoryReservationStatus.ACTIVE) {
            return Optional.of(reservation);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        reservation.setStatus(InventoryReservationStatus.EXPIRED);
        reservation.setCompletedAt(now);
        inventoryReservationRepository.save(reservation);

        if (reservation.getInventoryItemId() != null) {
            Optional<InventoryItem> itemOpt = inventoryItemRepository.findById(reservation.getInventoryItemId());
            if (itemOpt.isPresent()) {
                InventoryItem item = itemOpt.get();
                if (item.getStatus() == InventoryStatus.RESERVED
                        && reservation.getOrderReference().equals(item.getReservationReference())) {
                    item.setStatus(InventoryStatus.AVAILABLE);
                    item.setReservationReference(null);
                    item.setReservedUntil(null);
                    inventoryItemRepository.save(item);
                    log.info("Expired reservation ID {} and released physical item ID {}", reservationId, item.getId());
                }
            }
        }

        return Optional.of(reservation);
    }
}
