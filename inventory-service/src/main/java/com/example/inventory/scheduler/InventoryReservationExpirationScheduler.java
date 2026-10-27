package com.example.inventory.scheduler;

import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.repository.InventoryReservationRepository;
import com.example.inventory.service.ReservationStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class InventoryReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationExpirationScheduler.class);
    private static final int MAX_BATCH_LOOPS = 20; // Up to 1000 items per scan cycle

    private final InventoryReservationRepository inventoryReservationRepository;
    private final ReservationStateService reservationStateService;

    public InventoryReservationExpirationScheduler(
            InventoryReservationRepository inventoryReservationRepository,
            ReservationStateService reservationStateService
    ) {
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.reservationStateService = reservationStateService;
    }

    /**
     * Periodically scans for ACTIVE reservations whose TTL has expired.
     * Uses repeated first-batch fetching (no offset shift bug).
     * Processes each reservation in an independent transaction proxy (REQUIRES_NEW)
     * so that a failure in one record does not abort the remaining items.
     */
    @Scheduled(fixedDelayString = "${inventory.reservation.expiration-scan-delay:60000}")
    public void scanAndExpireReservations() {
        int loopCount = 0;
        int totalExpiredCount = 0;

        while (loopCount < MAX_BATCH_LOOPS) {
            Instant now = Instant.now();
            List<InventoryReservation> expiredBatch = inventoryReservationRepository
                    .findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                            InventoryReservationStatus.ACTIVE,
                            now
                    );

            if (expiredBatch.isEmpty()) {
                break;
            }

            for (InventoryReservation res : expiredBatch) {
                try {
                    reservationStateService.expireAndRelease(res.getId());
                    totalExpiredCount++;
                } catch (Exception ex) {
                    log.error("Failed to expire reservation ID {}: {}", res.getId(), ex.getMessage(), ex);
                }
            }

            loopCount++;
        }

        if (totalExpiredCount > 0) {
            log.info("Expiration scan completed: Expired and reclaimed {} reservations", totalExpiredCount);
        }
    }
}
