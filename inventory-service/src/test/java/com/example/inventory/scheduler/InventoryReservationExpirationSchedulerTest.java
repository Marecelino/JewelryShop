package com.example.inventory.scheduler;

import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.repository.InventoryReservationRepository;
import com.example.inventory.service.ReservationStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservationExpirationSchedulerTest {

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private ReservationStateService reservationStateService;

    @InjectMocks
    private InventoryReservationExpirationScheduler scheduler;

    private InventoryReservation buildReservation(Long id) {
        InventoryReservation res = new InventoryReservation();
        res.setId(id);
        res.setStatus(InventoryReservationStatus.ACTIVE);
        return res;
    }

    @Test
    @DisplayName("scanAndExpireReservations processes batches repeatedly until empty")
    void scanAndExpireReservations_processesBatchesUntilEmpty() {
        InventoryReservation r1 = buildReservation(1L);
        InventoryReservation r2 = buildReservation(2L);
        InventoryReservation r3 = buildReservation(3L);

        // First batch returns 2 items, second batch returns 1 item, third batch returns empty
        when(inventoryReservationRepository.findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(InventoryReservationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(r1, r2))
                .thenReturn(List.of(r3))
                .thenReturn(Collections.emptyList());

        when(reservationStateService.expireAndRelease(anyLong()))
                .thenReturn(Optional.of(buildReservation(99L)));

        scheduler.scanAndExpireReservations();

        verify(reservationStateService).expireAndRelease(1L);
        verify(reservationStateService).expireAndRelease(2L);
        verify(reservationStateService).expireAndRelease(3L);
        verify(inventoryReservationRepository, times(3))
                .findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(any(), any());
    }

    @Test
    @DisplayName("scanAndExpireReservations continues processing when individual record fails")
    void scanAndExpireReservations_continuesWhenSingleItemFails() {
        InventoryReservation r1 = buildReservation(1L);
        InventoryReservation r2 = buildReservation(2L);

        when(inventoryReservationRepository.findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(InventoryReservationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(r1, r2))
                .thenReturn(Collections.emptyList());

        // Item 1 throws exception, Item 2 succeeds
        when(reservationStateService.expireAndRelease(1L))
                .thenThrow(new RuntimeException("Simulated database failure on item 1"));
        when(reservationStateService.expireAndRelease(2L))
                .thenReturn(Optional.of(r2));

        scheduler.scanAndExpireReservations();

        verify(reservationStateService).expireAndRelease(1L);
        verify(reservationStateService).expireAndRelease(2L);
    }
}
