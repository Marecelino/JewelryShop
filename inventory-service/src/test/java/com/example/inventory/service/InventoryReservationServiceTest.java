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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ReservationStateService reservationStateService;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private InventoryReservationService inventoryReservationService;

    private InventoryReservation buildReservation(
            Long id,
            String orderRef,
            String itemRef,
            Integer productId,
            Long itemId,
            InventoryReservationStatus status,
            Instant expiresAt
    ) {
        InventoryReservation res = new InventoryReservation();
        res.setId(id);
        res.setOrderReference(orderRef);
        res.setOrderItemReference(itemRef);
        res.setProductId(productId);
        res.setInventoryItemId(itemId);
        res.setStatus(status);
        res.setTtlMinutes(15);
        res.setReservedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        res.setExpiresAt(expiresAt);
        res.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        res.setUpdatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        return res;
    }

    private InventoryItem buildInventoryItem(Long id, Integer productId, InventoryStatus status) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setProductId(productId);
        item.setBarcode("BC-" + id);
        item.setStatus(status);
        return item;
    }

    @Nested
    @DisplayName("reserve tests")
    class ReserveTests {

        @Test
        @DisplayName("reserve_whenNewValidRequest_createsReservationAndReturnsCreatedResult")
        void reserve_whenNewValidRequest_createsReservation() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1", "ORD-1-ITEM-1", 1, 15);
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ORD-1-ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-1", "ORD-1-ITEM-1"))
                    .thenReturn(Optional.empty());
            when(productClient.existsById(1)).thenReturn(true);
            when(reservationStateService.createReservationTransaction(request)).thenReturn(reservation);

            ReservationCreationResult result = inventoryReservationService.reserve(request);

            assertThat(result.newlyCreated()).isTrue();
            assertThat(result.response().reservationId()).isEqualTo(10L);
            assertThat(result.response().status()).isEqualTo(InventoryReservationStatus.ACTIVE);
            verify(reservationStateService).createReservationTransaction(request);
        }

        @Test
        @DisplayName("reserve_whenIdenticalRetry_returnsExistingWithoutCallingProductClient")
        void reserve_whenIdenticalRetry_returnsExistingWithoutCallingProductClient() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1", "ORD-1-ITEM-1", 1, 15);
            InventoryReservation existing = buildReservation(
                    10L, "ORD-1", "ORD-1-ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-1", "ORD-1-ITEM-1"))
                    .thenReturn(Optional.of(existing));

            ReservationCreationResult result = inventoryReservationService.reserve(request);

            assertThat(result.newlyCreated()).isFalse();
            assertThat(result.response().reservationId()).isEqualTo(10L);
            verifyNoInteractions(productClient);
            verify(reservationStateService, never()).createReservationTransaction(any());
        }

        @Test
        @DisplayName("reserve_whenConflictingPayload_throwsReservationConflictException")
        void reserve_whenConflictingPayload_throwsConflict() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1", "ORD-1-ITEM-1", 2, 15); // productId differs (2 vs 1)
            InventoryReservation existing = buildReservation(
                    10L, "ORD-1", "ORD-1-ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-1", "ORD-1-ITEM-1"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> inventoryReservationService.reserve(request))
                    .isInstanceOf(ReservationConflictException.class)
                    .hasMessageContaining("conflicting parameters");

            verifyNoInteractions(productClient);
        }

        @Test
        @DisplayName("reserve_whenProductNotFound_throwsProductNotFoundException")
        void reserve_whenProductNotFound_throwsProductNotFoundException() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1", "ORD-1-ITEM-1", 999, 15);

            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-1", "ORD-1-ITEM-1"))
                    .thenReturn(Optional.empty());
            when(productClient.existsById(999)).thenReturn(false);

            assertThatThrownBy(() -> inventoryReservationService.reserve(request))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(reservationStateService, never()).createReservationTransaction(any());
        }

        @Test
        @DisplayName("reserve_whenProductServiceDown_throwsDownstreamServiceUnavailableException")
        void reserve_whenProductServiceDown_throwsDownstreamUnavailable() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1", "ORD-1-ITEM-1", 1, 15);

            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-1", "ORD-1-ITEM-1"))
                    .thenReturn(Optional.empty());
            when(productClient.existsById(1))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> inventoryReservationService.reserve(request))
                    .isInstanceOf(DownstreamServiceUnavailableException.class)
                    .hasMessageContaining("Product Service is unavailable");
        }

        @Test
        @DisplayName("reserve_whenConcurrentRaceCollision_recoversWinningReservation")
        void reserve_whenConcurrentRaceCollision_recoversWinningReservation() {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-RACE", "ITEM-1", 1, 15);
            InventoryReservation winning = buildReservation(
                    20L, "ORD-RACE", "ITEM-1", 1, 105L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(15, ChronoUnit.MINUTES));

            // Initial check: not found
            when(inventoryReservationRepository.findByOrderReferenceAndOrderItemReference("ORD-RACE", "ITEM-1"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winning)); // second call after race

            when(productClient.existsById(1)).thenReturn(true);
            when(reservationStateService.createReservationTransaction(request))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint collision"));

            ReservationCreationResult result = inventoryReservationService.reserve(request);

            assertThat(result.newlyCreated()).isFalse();
            assertThat(result.response().reservationId()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("commitReservation tests")
    class CommitTests {

        @Test
        @DisplayName("commitReservation_whenActiveAndNotExpired_commitsSuccessfullyAndMarksItemSold")
        void commitReservation_whenActive_commitsSuccessfully() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.MINUTES));
            InventoryItem item = buildInventoryItem(100L, 1, InventoryStatus.RESERVED);
            item.setReservationReference("ORD-1");
            item.setReservedUntil(reservation.getExpiresAt());

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));
            when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(inventoryReservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryReservationResponse response = inventoryReservationService.commitReservation(10L);

            assertThat(response.status()).isEqualTo(InventoryReservationStatus.COMMITTED);
            assertThat(response.completedAt()).isNotNull();
            assertThat(item.getStatus()).isEqualTo(InventoryStatus.SOLD);
            assertThat(item.getReservationReference()).isNull();
            assertThat(item.getReservedUntil()).isNull();
            verify(inventoryItemRepository).save(item);
        }

        @Test
        @DisplayName("commitReservation_whenAlreadyCommitted_returnsIdempotently")
        void commitReservation_whenAlreadyCommitted_returnsIdempotently() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.COMMITTED, Instant.now().plus(10, ChronoUnit.MINUTES));
            reservation.setCompletedAt(Instant.now());

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));

            InventoryReservationResponse response = inventoryReservationService.commitReservation(10L);

            assertThat(response.status()).isEqualTo(InventoryReservationStatus.COMMITTED);
            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("commitReservation_whenExpired_marksExpiredAndReleasesItemAndThrowsConflict")
        void commitReservation_whenExpired_marksExpiredAndReleasesItemAndThrowsConflict() {
            // Expired 5 minutes ago
            Instant pastTime = Instant.now().minus(5, ChronoUnit.MINUTES);
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, pastTime);
            InventoryItem item = buildInventoryItem(100L, 1, InventoryStatus.RESERVED);
            item.setReservationReference("ORD-1");
            item.setReservedUntil(pastTime);

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));
            when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> inventoryReservationService.commitReservation(10L))
                    .isInstanceOf(ReservationConflictException.class)
                    .hasMessageContaining("Reservation has expired");

            assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.EXPIRED);
            assertThat(reservation.getCompletedAt()).isNotNull();
            assertThat(item.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            assertThat(item.getReservationReference()).isNull();
            assertThat(item.getReservedUntil()).isNull();
            verify(inventoryReservationRepository).save(reservation);
            verify(inventoryItemRepository).save(item);
        }

        @Test
        @DisplayName("commitReservation_whenReleased_throwsConflict")
        void commitReservation_whenReleased_throwsConflict() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.RELEASED, Instant.now().plus(10, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> inventoryReservationService.commitReservation(10L))
                    .isInstanceOf(ReservationConflictException.class)
                    .hasMessageContaining("Cannot commit reservation with status: RELEASED");
        }

        @Test
        @DisplayName("commitReservation_whenNotFound_throwsNotFound")
        void commitReservation_whenNotFound_throwsNotFound() {
            when(inventoryReservationRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryReservationService.commitReservation(999L))
                    .isInstanceOf(ReservationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("releaseReservation tests")
    class ReleaseTests {

        @Test
        @DisplayName("releaseReservation_whenActive_releasesSuccessfullyAndMarksItemAvailable")
        void releaseReservation_whenActive_releasesSuccessfully() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.MINUTES));
            InventoryItem item = buildInventoryItem(100L, 1, InventoryStatus.RESERVED);
            item.setReservationReference("ORD-1");
            item.setReservedUntil(reservation.getExpiresAt());

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));
            when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(inventoryReservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryReservationResponse response = inventoryReservationService.releaseReservation(10L);

            assertThat(response.status()).isEqualTo(InventoryReservationStatus.RELEASED);
            assertThat(response.completedAt()).isNotNull();
            assertThat(item.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            assertThat(item.getReservationReference()).isNull();
            assertThat(item.getReservedUntil()).isNull();
            verify(inventoryItemRepository).save(item);
        }

        @Test
        @DisplayName("releaseReservation_whenAlreadyReleased_returnsIdempotently")
        void releaseReservation_whenAlreadyReleased_returnsIdempotently() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.RELEASED, Instant.now().plus(10, ChronoUnit.MINUTES));
            reservation.setCompletedAt(Instant.now());

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));

            InventoryReservationResponse response = inventoryReservationService.releaseReservation(10L);

            assertThat(response.status()).isEqualTo(InventoryReservationStatus.RELEASED);
            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("releaseReservation_whenCommitted_throwsConflict")
        void releaseReservation_whenCommitted_throwsConflict() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.COMMITTED, Instant.now().plus(10, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> inventoryReservationService.releaseReservation(10L))
                    .isInstanceOf(ReservationConflictException.class)
                    .hasMessageContaining("Cannot release already committed/sold reservation");
        }

        @Test
        @DisplayName("releaseReservation_whenExpired_throwsConflict")
        void releaseReservation_whenExpired_throwsConflict() {
            InventoryReservation reservation = buildReservation(
                    10L, "ORD-1", "ITEM-1", 1, 100L,
                    InventoryReservationStatus.EXPIRED, Instant.now().minus(10, ChronoUnit.MINUTES));

            when(inventoryReservationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> inventoryReservationService.releaseReservation(10L))
                    .isInstanceOf(ReservationConflictException.class)
                    .hasMessageContaining("Reservation has already expired and stock was reclaimed");
        }
    }
}
