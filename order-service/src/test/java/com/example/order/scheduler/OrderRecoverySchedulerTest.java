package com.example.order.scheduler;

import com.example.order.client.InventoryReservationClient;
import com.example.order.dto.external.CreateReservationRequestDto;
import com.example.order.dto.external.InventoryReservationDto;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.DownstreamServiceException;
import com.example.order.exception.ReservationConflictException;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.order.service.OrderStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRecoverySchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStateService orderStateService;

    @Mock
    private OrderService orderService;

    @Mock
    private InventoryReservationClient inventoryReservationClient;

    @InjectMocks
    private OrderRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "maxRetryAttempts", 5);
        ReflectionTestUtils.setField(scheduler, "retryBaseDelayMs", 5000L);
        ReflectionTestUtils.setField(scheduler, "retryMaxDelayMs", 300000L);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
        ReflectionTestUtils.setField(scheduler, "reservationTtlMinutes", 15);
    }

    @Test
    @DisplayName("1. Exponential backoff calculation respects exponential curve and ceiling")
    void testBackoffDelay() {
        assertThat(scheduler.calculateBackoffDelay(1)).isEqualTo(5000L);
        assertThat(scheduler.calculateBackoffDelay(2)).isEqualTo(10000L);
        assertThat(scheduler.calculateBackoffDelay(3)).isEqualTo(20000L);
        assertThat(scheduler.calculateBackoffDelay(4)).isEqualTo(40000L);
        assertThat(scheduler.calculateBackoffDelay(10)).isEqualTo(300000L); // Capped at max 300s
    }

    @Test
    @DisplayName("2. Recover pending reservation: ACTIVE response transitions item to RESERVED and calls safe completion check")
    void recoverPendingReservations_Active() {
        Order order = new Order("ORD-REC-1", 10L, new BigDecimal("1000.00"), "key-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.PENDING_RESERVATION);

        OrderItem item = new OrderItem("ITEM-REC-1", 1, "Gold Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(200L);
        item.setStatus(OrderItemStatus.PENDING_RESERVATION);
        item.setRetryCount(1);
        order.addItem(item);

        when(orderItemRepository.findDueForRetry(eq(OrderItemStatus.PENDING_RESERVATION), eq(List.of(OrderStatus.PENDING_RESERVATION)), any(), any()))
                .thenReturn(List.of(item));

        InventoryReservationDto res = new InventoryReservationDto(
                501L, "ORD-REC-1", "ITEM-REC-1", 1, 1001L, "ACTIVE", 15,
                Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class))).thenReturn(res);

        scheduler.recoverPendingReservations();

        verify(orderStateService).updateOrderItemSuccess(200L, 501L, 1001L, OrderItemStatus.RESERVED);
        verify(orderStateService).checkAndCompleteOrderReservation(100L);
    }

    @Test
    @DisplayName("3. Recover pending reservation: Failed parent order is skipped and NEVER reserved")
    void recoverPendingReservations_FailedParentOrder_NeverReserved() {
        Order failedOrder = new Order("ORD-FAILED", 10L, new BigDecimal("1000.00"), "key-fail");
        failedOrder.setOrderId(105L);
        failedOrder.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-F-1", 1, "Gold Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(205L);
        item.setStatus(OrderItemStatus.PENDING_RESERVATION);
        failedOrder.addItem(item);

        // Even if repository returned it or memory object had failed status
        when(orderItemRepository.findDueForRetry(eq(OrderItemStatus.PENDING_RESERVATION), eq(List.of(OrderStatus.PENDING_RESERVATION)), any(), any()))
                .thenReturn(List.of(item));

        scheduler.recoverPendingReservations();

        verifyNoInteractions(inventoryReservationClient);
        verify(orderStateService, never()).updateOrderItemSuccess(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("4. Recover pending reservation: Max retries exceeded transitions to RECONCILIATION_REQUIRED")
    void recoverPendingReservations_MaxRetriesExceeded() {
        Order order = new Order("ORD-REC-2", 10L, new BigDecimal("1000.00"), "key-2");
        order.setOrderId(101L);
        order.setStatus(OrderStatus.PENDING_RESERVATION);

        OrderItem item = new OrderItem("ITEM-REC-2", 1, "Gold Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(201L);
        item.setStatus(OrderItemStatus.PENDING_RESERVATION);
        item.setRetryCount(5); // Equal to maxRetryAttempts
        order.addItem(item);

        when(orderItemRepository.findDueForRetry(eq(OrderItemStatus.PENDING_RESERVATION), eq(List.of(OrderStatus.PENDING_RESERVATION)), any(), any()))
                .thenReturn(List.of(item));

        scheduler.recoverPendingReservations();

        verify(orderStateService).updateOrderItemFailure(201L, OrderItemStatus.RECONCILIATION_REQUIRED, "MAX_RETRIES_EXCEEDED", null, 5);
        verify(orderStateService).updateOrderStatus(101L, OrderStatus.RECONCILIATION_REQUIRED);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("5. Recover compensation: release success transitions item to RELEASED")
    void recoverCompensation_Success() {
        Order order = new Order("ORD-COMP-1", 10L, new BigDecimal("1000.00"), "key-c1");
        order.setOrderId(110L);
        order.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-C1", 1, "Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(210L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(901L);
        item.setRetryCount(0);
        order.addItem(item);

        when(orderItemRepository.findItemsNeedingCompensation(any(), any(), any(), any()))
                .thenReturn(List.of(item));

        scheduler.recoverCompensationForFailedOrders();

        verify(inventoryReservationClient).releaseReservation(901L);
        verify(orderStateService).updateOrderItemStatus(210L, OrderItemStatus.RELEASED);
    }

    @Test
    @DisplayName("6. Recover compensation: release timeout updates retry count and backoff nextRetryAt")
    void recoverCompensation_Timeout_Backoff() {
        Order order = new Order("ORD-COMP-2", 10L, new BigDecimal("1000.00"), "key-c2");
        order.setOrderId(111L);
        order.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-C2", 1, "Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(211L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(902L);
        item.setRetryCount(1);
        order.addItem(item);

        when(orderItemRepository.findItemsNeedingCompensation(any(), any(), any(), any()))
                .thenReturn(List.of(item));
        doThrow(new DownstreamServiceException("inventory-service", 503, "Timeout"))
                .when(inventoryReservationClient).releaseReservation(902L);

        scheduler.recoverCompensationForFailedOrders();

        verify(orderStateService).updateOrderItemFailure(eq(211L), eq(OrderItemStatus.RESERVED), eq("RELEASE_TIMEOUT"), any(Instant.class), eq(2));
    }

    @Test
    @DisplayName("7. Recover compensation: conflict queries remote EXPIRED and marks item EXPIRED")
    void recoverCompensation_Conflict_RemoteExpired() {
        Order order = new Order("ORD-COMP-3", 10L, new BigDecimal("1000.00"), "key-c3");
        order.setOrderId(112L);
        order.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-C3", 1, "Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(212L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(903L);
        order.addItem(item);

        when(orderItemRepository.findItemsNeedingCompensation(any(), any(), any(), any()))
                .thenReturn(List.of(item));
        doThrow(new ReservationConflictException("ORD-COMP-3", "ITEM-C3", "Already expired"))
                .when(inventoryReservationClient).releaseReservation(903L);

        InventoryReservationDto remoteExpired = new InventoryReservationDto(
                903L, "ORD-COMP-3", "ITEM-C3", 1, 1001L, "EXPIRED", 15,
                Instant.now().minusSeconds(1000), Instant.now().minusSeconds(100), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.getReservationById(903L)).thenReturn(remoteExpired);

        scheduler.recoverCompensationForFailedOrders();

        verify(orderStateService).updateOrderItemStatus(212L, OrderItemStatus.EXPIRED);
    }

    @Test
    @DisplayName("8. Recover compensation: conflict queries remote COMMITTED and flags RECONCILIATION_REQUIRED")
    void recoverCompensation_Conflict_RemoteCommitted() {
        Order order = new Order("ORD-COMP-4", 10L, new BigDecimal("1000.00"), "key-c4");
        order.setOrderId(113L);
        order.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-C4", 1, "Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(213L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(904L);
        order.addItem(item);

        when(orderItemRepository.findItemsNeedingCompensation(any(), any(), any(), any()))
                .thenReturn(List.of(item));
        doThrow(new ReservationConflictException("ORD-COMP-4", "ITEM-C4", "Already committed"))
                .when(inventoryReservationClient).releaseReservation(904L);

        InventoryReservationDto remoteCommitted = new InventoryReservationDto(
                904L, "ORD-COMP-4", "ITEM-C4", 1, 1001L, "COMMITTED", 15,
                Instant.now().minusSeconds(1000), Instant.now().plusSeconds(500), Instant.now(), Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.getReservationById(904L)).thenReturn(remoteCommitted);

        scheduler.recoverCompensationForFailedOrders();

        verify(orderStateService).updateOrderItemStatus(213L, OrderItemStatus.COMMITTED);
        verify(orderStateService).updateOrderStatus(113L, OrderStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("9. Recover compensation: max retries reached transitions item and order to RECONCILIATION_REQUIRED")
    void recoverCompensation_MaxRetriesReached() {
        Order order = new Order("ORD-COMP-5", 10L, new BigDecimal("1000.00"), "key-c5");
        order.setOrderId(114L);
        order.setStatus(OrderStatus.RESERVATION_FAILED);

        OrderItem item = new OrderItem("ITEM-C5", 1, "Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(214L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(905L);
        item.setRetryCount(5); // At maximum
        order.addItem(item);

        when(orderItemRepository.findItemsNeedingCompensation(any(), any(), any(), any()))
                .thenReturn(List.of(item));

        scheduler.recoverCompensationForFailedOrders();

        verify(orderStateService).updateOrderItemFailure(214L, OrderItemStatus.RECONCILIATION_REQUIRED, "MAX_RETRIES_EXCEEDED", null, 5);
        verify(orderStateService).updateOrderStatus(114L, OrderStatus.RECONCILIATION_REQUIRED);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("10. Recover pending cancellation: releases remaining items and marks CANCELLED when terminal")
    void recoverPendingCancellations_Success() {
        Order order = new Order("ORD-CANCEL-1", 10L, new BigDecimal("1000.00"), "key-3");
        order.setOrderId(102L);
        order.setStatus(OrderStatus.CANCELLATION_PENDING);

        OrderItem item = new OrderItem("ITEM-C-1", 1, "Gold Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(202L);
        item.setStatus(OrderItemStatus.RELEASED);
        order.addItem(item);

        when(orderRepository.findByStatusInWithItems(eq(List.of(OrderStatus.CANCELLATION_PENDING)), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findWithItemsByOrderReference("ORD-CANCEL-1")).thenReturn(Optional.of(order));

        scheduler.recoverPendingCancellations();

        verify(orderService).compensateReservedItems(order);
        verify(orderStateService).updateOrderStatus(102L, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("11. Reconcile AWAITING_PAYMENT: detects remote EXPIRED reservation and triggers compensation")
    void reconcileExpiredAwaitingPayments_DetectedExpired() {
        Order order = new Order("ORD-EXPIRE-SCAN", 10L, new BigDecimal("1000.00"), "key-4");
        order.setOrderId(103L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setUpdatedAt(Instant.now().minus(20, ChronoUnit.MINUTES)); // Over 15 min TTL

        OrderItem item = new OrderItem("ITEM-E-1", 1, "Gold Ring", new BigDecimal("1000.00"));
        item.setOrderItemId(203L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(777L);
        order.addItem(item);

        when(orderRepository.findByStatusInWithItems(eq(List.of(OrderStatus.AWAITING_PAYMENT)), any()))
                .thenReturn(List.of(order));

        InventoryReservationDto remoteExpired = new InventoryReservationDto(
                777L, "ORD-EXPIRE-SCAN", "ITEM-E-1", 1, 1001L, "EXPIRED", 15,
                Instant.now().minusSeconds(1200), Instant.now().minusSeconds(300), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.getReservationById(777L)).thenReturn(remoteExpired);

        scheduler.reconcileExpiredAwaitingPayments();

        verify(orderStateService).updateOrderItemStatus(203L, OrderItemStatus.EXPIRED);
        verify(orderStateService).updateOrderStatus(103L, OrderStatus.RESERVATION_EXPIRED);
        verify(orderService).compensateReservedItems(order);
    }
}
