package com.example.inventory.service;

import com.example.inventory.client.ProductClient;
import com.example.inventory.dto.request.CreateInventoryReservationRequest;
import com.example.inventory.dto.response.InventoryReservationResponse;
import com.example.inventory.dto.response.ReservationCreationResult;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.enums.InventoryStatus;
import com.example.inventory.exception.DownstreamServiceUnavailableException;
import com.example.inventory.exception.ProductNotFoundException;
import com.example.inventory.exception.ReservationConflictException;
import com.example.inventory.exception.ReservationNotFoundException;
import com.example.inventory.repository.InventoryItemRepository;
import com.example.inventory.repository.InventoryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ReservationStateService reservationStateService;
    private final ProductClient productClient;

    public InventoryReservationService(
            InventoryReservationRepository inventoryReservationRepository,
            InventoryItemRepository inventoryItemRepository,
            ReservationStateService reservationStateService,
            ProductClient productClient
    ) {
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationStateService = reservationStateService;
        this.productClient = productClient;
    }

    /**
     * Reserves an inventory item for an order line.
     * Step 1: Check existing reservation for (orderReference, orderItemReference).
     *         If exists and payload matches, return it (idempotent, 200 OK) without calling Product Service.
     *         If exists and payload conflicts, throw 409 Conflict.
     * Step 2: Validate product existence with Product Service.
     * Step 3: Atomically select available item with lock and persist reservation in isolated transaction.
     * Step 4: Handle concurrent duplicate collisions gracefully via unique constraint.
     */
    public ReservationCreationResult reserve(CreateInventoryReservationRequest request) {
        // Step 1: Check if this business identity has already been reserved
        Optional<InventoryReservation> existingOpt = inventoryReservationRepository
                .findByOrderReferenceAndOrderItemReference(
                        request.getOrderReference(),
                        request.getOrderItemReference()
                );

        if (existingOpt.isPresent()) {
            InventoryReservation existing = existingOpt.get();
            if (existing.getProductId().equals(request.getProductId())
                    && existing.getTtlMinutes().equals(request.getTtlMinutes())) {
                log.info("Idempotent retry for reservation ID {} (order: {}, item: {})",
                        existing.getId(), request.getOrderReference(), request.getOrderItemReference());
                return new ReservationCreationResult(toResponse(existing), false);
            }
            throw new ReservationConflictException(
                    "Reservation already exists with conflicting parameters for order: "
                            + request.getOrderReference() + ", item: " + request.getOrderItemReference()
            );
        }

        // Step 2: Validate product existence with Product Service
        verifyProductExists(request.getProductId());

        // Step 3 & 4: Attempt reservation creation with concurrent collision safety
        try {
            InventoryReservation created = reservationStateService.createReservationTransaction(request);
            log.info("Created new reservation ID {} for item ID {}", created.getId(), created.getInventoryItemId());
            return new ReservationCreationResult(toResponse(created), true);

        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: Another transaction with the same (orderRef, orderItemRef) committed first.
            // Safely read the winning reservation from the database in a clean read.
            InventoryReservation winning = inventoryReservationRepository
                    .findByOrderReferenceAndOrderItemReference(
                            request.getOrderReference(),
                            request.getOrderItemReference()
                    )
                    .orElseThrow(() -> ex);

            if (winning.getProductId().equals(request.getProductId())
                    && winning.getTtlMinutes().equals(request.getTtlMinutes())) {
                log.info("Resolved concurrent race: Returning winning reservation ID {}", winning.getId());
                return new ReservationCreationResult(toResponse(winning), false);
            }

            throw new ReservationConflictException(
                    "Reservation already exists with conflicting parameters for order: "
                            + request.getOrderReference() + ", item: " + request.getOrderItemReference()
            );
        }
    }

    /**
     * Retrieves reservation details by reservationId.
     */
    @Transactional(readOnly = true)
    public InventoryReservationResponse getReservationById(Long reservationId) {
        InventoryReservation reservation = inventoryReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return toResponse(reservation);
    }

    /**
     * Commits an active reservation after confirmed payment success.
     * Transitions item to SOLD, reservation to COMMITTED.
     * If expired, safely delegates to isolated transaction to expire and release stock before throwing 409.
     */
    @Transactional(noRollbackFor = ReservationConflictException.class)
    public InventoryReservationResponse commitReservation(Long reservationId) {
        InventoryReservation reservation = inventoryReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // Idempotent retry: already committed
        if (reservation.getStatus() == InventoryReservationStatus.COMMITTED) {
            return toResponse(reservation);
        }

        // Reject if in terminal status
        if (reservation.getStatus() == InventoryReservationStatus.RELEASED
                || reservation.getStatus() == InventoryReservationStatus.EXPIRED) {
            throw new ReservationConflictException(
                    "Cannot commit reservation with status: " + reservation.getStatus()
            );
        }

        // Check if reservation expired
        if (reservation.getExpiresAt().isBefore(Instant.now())) {
            // Transition reservation to EXPIRED
            reservation.setStatus(InventoryReservationStatus.EXPIRED);
            reservation.setCompletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            inventoryReservationRepository.save(reservation);

            // Release physical item to AVAILABLE
            InventoryItem item = inventoryItemRepository.findById(reservation.getInventoryItemId())
                    .orElseThrow(() -> new ReservationConflictException(
                            "Associated inventory item not found: " + reservation.getInventoryItemId()
                    ));

            if (item.getStatus() == InventoryStatus.RESERVED
                    && reservation.getOrderReference().equals(item.getReservationReference())) {
                item.setStatus(InventoryStatus.AVAILABLE);
                item.setReservationReference(null);
                item.setReservedUntil(null);
                inventoryItemRepository.save(item);
                log.info("Expired reservation ID {} on commit attempt and released item ID {}", reservationId, item.getId());
            }

            throw new ReservationConflictException(
                    "Reservation has expired at " + reservation.getExpiresAt() + " and stock was returned to inventory"
            );
        }

        // Transition physical item to SOLD
        InventoryItem item = inventoryItemRepository.findById(reservation.getInventoryItemId())
                .orElseThrow(() -> new ReservationConflictException(
                        "Associated inventory item not found: " + reservation.getInventoryItemId()
                ));

        if (item.getStatus() != InventoryStatus.RESERVED
                || !reservation.getOrderReference().equals(item.getReservationReference())) {
            throw new ReservationConflictException(
                    "Item " + item.getId() + " is not currently reserved for order " + reservation.getOrderReference()
            );
        }

        item.setStatus(InventoryStatus.SOLD);
        item.setReservationReference(null);
        item.setReservedUntil(null);
        inventoryItemRepository.save(item);

        // Transition reservation to COMMITTED
        reservation.setStatus(InventoryReservationStatus.COMMITTED);
        reservation.setCompletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        InventoryReservation saved = inventoryReservationRepository.save(reservation);

        log.info("Committed reservation ID {}, item ID {} is now SOLD", reservation.getId(), item.getId());
        return toResponse(saved);
    }

    /**
     * Releases a reservation when order is cancelled or payment definitively failed.
     * Transitions item to AVAILABLE, reservation to RELEASED.
     */
    @Transactional
    public InventoryReservationResponse releaseReservation(Long reservationId) {
        InventoryReservation reservation = inventoryReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // Idempotent retry: already released
        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            return toResponse(reservation);
        }

        // Reject releasing already sold item
        if (reservation.getStatus() == InventoryReservationStatus.COMMITTED) {
            throw new ReservationConflictException(
                    "Cannot release already committed/sold reservation with ID: " + reservationId
            );
        }

        // Reject releasing an already expired reservation
        if (reservation.getStatus() == InventoryReservationStatus.EXPIRED) {
            throw new ReservationConflictException(
                    "Reservation has already expired and stock was reclaimed. Cannot release an expired reservation"
            );
        }

        // Transition physical item to AVAILABLE
        InventoryItem item = inventoryItemRepository.findById(reservation.getInventoryItemId())
                .orElseThrow(() -> new ReservationConflictException(
                        "Associated inventory item not found: " + reservation.getInventoryItemId()
                ));

        if (item.getStatus() == InventoryStatus.RESERVED
                && reservation.getOrderReference().equals(item.getReservationReference())) {
            item.setStatus(InventoryStatus.AVAILABLE);
            item.setReservationReference(null);
            item.setReservedUntil(null);
            inventoryItemRepository.save(item);
        } else {
            throw new ReservationConflictException(
                    "Item " + item.getId() + " is not currently reserved for order " + reservation.getOrderReference()
            );
        }

        // Transition reservation to RELEASED
        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservation.setCompletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        InventoryReservation saved = inventoryReservationRepository.save(reservation);

        log.info("Released reservation ID {}, item ID {} is now AVAILABLE", reservation.getId(), item.getId());
        return toResponse(saved);
    }

    private void verifyProductExists(Integer productId) {
        try {
            if (!productClient.existsById(productId)) {
                throw new ProductNotFoundException(productId);
            }
        } catch (ProductNotFoundException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ProductNotFoundException(productId);
            }
            throw new DownstreamServiceUnavailableException(
                    "Product Service returned error: " + ex.getStatusCode()
            );
        } catch (Exception ex) {
            throw new DownstreamServiceUnavailableException(
                    "Product Service is unavailable or connection failed: " + ex.getMessage()
            );
        }
    }

    public InventoryReservationResponse toResponse(InventoryReservation reservation) {
        return new InventoryReservationResponse(
                reservation.getId(),
                reservation.getOrderReference(),
                reservation.getOrderItemReference(),
                reservation.getProductId(),
                reservation.getInventoryItemId(),
                reservation.getStatus(),
                reservation.getTtlMinutes(),
                reservation.getReservedAt(),
                reservation.getExpiresAt(),
                reservation.getCompletedAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
