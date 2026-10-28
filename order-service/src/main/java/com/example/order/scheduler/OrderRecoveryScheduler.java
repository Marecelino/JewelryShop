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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class OrderRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStateService orderStateService;
    private final OrderService orderService;
    private final InventoryReservationClient inventoryReservationClient;

    @Value("${app.order.max-retry-attempts:5}")
    private int maxRetryAttempts;

    @Value("${app.order.retry-base-delay-ms:5000}")
    private long retryBaseDelayMs;

    @Value("${app.order.retry-max-delay-ms:300000}")
    private long retryMaxDelayMs;

    @Value("${app.order.recovery-batch-size:50}")
    private int batchSize;

    @Value("${app.order.reservation-ttl-minutes:15}")
    private int reservationTtlMinutes;

    public OrderRecoveryScheduler(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStateService orderStateService,
            OrderService orderService,
            InventoryReservationClient inventoryReservationClient
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStateService = orderStateService;
        this.orderService = orderService;
        this.inventoryReservationClient = inventoryReservationClient;
    }

    @Scheduled(fixedDelayString = "${app.order.recovery-scan-delay-ms:60000}")
    public void runRecoveryScan() {
        try {
            recoverPendingReservations();
            recoverPendingCancellations();
            recoverCompensationForFailedOrders();
            reconcileExpiredAwaitingPayments();
        } catch (Exception e) {
            log.error("Unexpected error in OrderRecoveryScheduler cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Pass 1: Recover items in PENDING_RESERVATION due for retry.
     * Never reserves a pending item if parent order is not PENDING_RESERVATION.
     */
    public void recoverPendingReservations() {
        Instant now = Instant.now();
        List<OrderItem> pendingItems = orderItemRepository.findDueForRetry(
                OrderItemStatus.PENDING_RESERVATION,
                List.of(OrderStatus.PENDING_RESERVATION),
                now,
                PageRequest.of(0, batchSize)
        );

        for (OrderItem item : pendingItems) {
            try {
                processPendingReservationItem(item);
            } catch (Exception e) {
                log.error("Error processing pending reservation for item {}: {}", item.getOrderItemReference(), e.getMessage());
            }
        }
    }

    private void processPendingReservationItem(OrderItem item) {
        Order order = item.getOrder();
        if (order == null || order.getStatus() != OrderStatus.PENDING_RESERVATION) {
            log.info("Skipping recovery for item {} because parent order is not PENDING_RESERVATION (status: {})",
                    item.getOrderItemReference(), order != null ? order.getStatus() : "null");
            return;
        }

        int attempts = item.getRetryCount() != null ? item.getRetryCount() : 0;

        if (attempts >= maxRetryAttempts) {
            log.warn("Item {} exceeded max retry attempts ({}). Transitioning to RECONCILIATION_REQUIRED.",
                    item.getOrderItemReference(), maxRetryAttempts);
            orderStateService.updateOrderItemFailure(
                    item.getOrderItemId(),
                    OrderItemStatus.RECONCILIATION_REQUIRED,
                    "MAX_RETRIES_EXCEEDED",
                    null,
                    attempts
            );
            orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
            return;
        }

        try {
            CreateReservationRequestDto req = new CreateReservationRequestDto(
                    order.getOrderReference(),
                    item.getOrderItemReference(),
                    item.getProductId(),
                    reservationTtlMinutes
            );

            InventoryReservationDto res = inventoryReservationClient.reserve(req);

            if (res == null) {
                log.error("Null reservation response for item {}", item.getOrderItemReference());
                orderStateService.updateOrderItemFailure(
                        item.getOrderItemId(),
                        OrderItemStatus.RECONCILIATION_REQUIRED,
                        "NULL_RESERVATION_RESPONSE",
                        null,
                        attempts + 1
                );
                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                return;
            }

            if (res.isActive()) {
                item.setStatus(OrderItemStatus.RESERVED);
                item.setReservationId(res.reservationId());
                item.setInventoryItemId(res.inventoryItemId());
                orderStateService.updateOrderItemSuccess(
                        item.getOrderItemId(),
                        res.reservationId(),
                        res.inventoryItemId(),
                        OrderItemStatus.RESERVED
                );
                // Transactional check with status guard preventing transition if order became cancelled/failed
                orderStateService.checkAndCompleteOrderReservation(order.getOrderId());
            } else if (res.isExpired()) {
                log.warn("Reservation returned EXPIRED for item {}", item.getOrderItemReference());
                orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.EXPIRED);
                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RESERVATION_EXPIRED);
                orderStateService.failRemainingPendingItems(order.getOrderId());
                orderService.compensateReservedItems(order);
            } else if (res.isReleased()) {
                log.warn("Reservation returned RELEASED during reservation recovery for item {}", item.getOrderItemReference());
                orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RELEASED);
                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                orderStateService.failRemainingPendingItems(order.getOrderId());
            } else if (res.isCommitted()) {
                log.error("Unexpected COMMITTED status from inventory for item {}", item.getOrderItemReference());
                orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.COMMITTED);
                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                orderStateService.failRemainingPendingItems(order.getOrderId());
            } else {
                log.error("Unrecognized reservation status '{}' for item {}", res.status(), item.getOrderItemReference());
                orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                orderStateService.failRemainingPendingItems(order.getOrderId());
            }
        } catch (ReservationConflictException e) {
            log.warn("Reservation conflict during recovery for item {}: {}", item.getOrderItemReference(), e.getMessage());
            orderStateService.updateOrderItemFailure(
                    item.getOrderItemId(),
                    OrderItemStatus.RESERVATION_FAILED,
                    "OUT_OF_STOCK",
                    null,
                    attempts + 1
            );
            orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RESERVATION_FAILED);
            // Ensure remaining unprocessed items cannot later be reserved
            orderStateService.failRemainingPendingItems(order.getOrderId());
            orderService.compensateReservedItems(order);
        } catch (DownstreamServiceException e) {
            long backoff = calculateBackoffDelay(attempts + 1);
            Instant nextRetry = Instant.now().plusMillis(backoff);
            orderStateService.updateOrderItemFailure(
                    item.getOrderItemId(),
                    OrderItemStatus.PENDING_RESERVATION,
                    "DOWNSTREAM_TIMEOUT",
                    nextRetry,
                    attempts + 1
            );
        }
    }

    /**
     * Pass 2: Recover items in CANCELLATION_PENDING.
     */
    public void recoverPendingCancellations() {
        List<Order> pendingOrders = orderRepository.findByStatusInWithItems(
                List.of(OrderStatus.CANCELLATION_PENDING),
                PageRequest.of(0, batchSize)
        );

        for (Order order : pendingOrders) {
            try {
                orderService.compensateReservedItems(order);
                orderRepository.findWithItemsByOrderReference(order.getOrderReference()).ifPresent(reloaded -> {
                    boolean allTerminal = reloaded.getItems().stream()
                            .allMatch(i -> i.getStatus() == OrderItemStatus.RELEASED
                                    || i.getStatus() == OrderItemStatus.EXPIRED
                                    || i.getStatus() == OrderItemStatus.RESERVATION_FAILED);

                    if (allTerminal) {
                        orderStateService.updateOrderStatus(reloaded.getOrderId(), OrderStatus.CANCELLED);
                        log.info("Order {} transitioned to CANCELLED by recovery scheduler", reloaded.getOrderReference());
                    }
                });
            } catch (Exception e) {
                log.error("Error processing pending cancellation for order {}: {}", order.getOrderReference(), e.getMessage());
            }
        }
    }

    /**
     * Pass 3: Recover compensation for failed or expired orders that still have RESERVED items.
     * Uses bounded retries, nextRetryAt backoff, and reconciliation for conflicts.
     */
    public void recoverCompensationForFailedOrders() {
        Instant now = Instant.now();
        List<OrderItem> itemsNeedingComp = orderItemRepository.findItemsNeedingCompensation(
                List.of(OrderItemStatus.RESERVED),
                List.of(OrderStatus.RESERVATION_FAILED, OrderStatus.RESERVATION_EXPIRED, OrderStatus.CANCELLED),
                now,
                PageRequest.of(0, batchSize)
        );

        for (OrderItem item : itemsNeedingComp) {
            int attempts = item.getRetryCount() != null ? item.getRetryCount() : 0;
            if (attempts >= maxRetryAttempts) {
                log.warn("Item {} compensation release exceeded max retry attempts ({}). Transitioning to RECONCILIATION_REQUIRED.",
                        item.getOrderItemReference(), maxRetryAttempts);
                orderStateService.updateOrderItemFailure(
                        item.getOrderItemId(),
                        OrderItemStatus.RECONCILIATION_REQUIRED,
                        "MAX_RETRIES_EXCEEDED",
                        null,
                        attempts
                );
                if (item.getOrder() != null) {
                    orderStateService.updateOrderStatus(item.getOrder().getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                }
                continue;
            }

            try {
                if (item.getReservationId() != null) {
                    inventoryReservationClient.releaseReservation(item.getReservationId());
                    orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RELEASED);
                }
            } catch (ReservationConflictException e) {
                log.warn("Release conflict for item {}. Querying remote reservation state.", item.getOrderItemReference());
                try {
                    InventoryReservationDto remote = inventoryReservationClient.getReservationById(item.getReservationId());
                    if (remote.isReleased()) {
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RELEASED);
                    } else if (remote.isExpired()) {
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.EXPIRED);
                    } else if (remote.isCommitted()) {
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.COMMITTED);
                        if (item.getOrder() != null) {
                            orderStateService.updateOrderStatus(item.getOrder().getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        }
                    } else {
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                        if (item.getOrder() != null) {
                            orderStateService.updateOrderStatus(item.getOrder().getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        }
                    }
                } catch (Exception inspectEx) {
                    log.error("Failed to inspect remote reservation for item {}: {}", item.getOrderItemReference(), inspectEx.getMessage());
                    orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                    if (item.getOrder() != null) {
                        orderStateService.updateOrderStatus(item.getOrder().getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                    }
                }
            } catch (DownstreamServiceException e) {
                log.warn("Timeout/network failure releasing item {}: attempt {}", item.getOrderItemReference(), attempts + 1);
                long backoff = calculateBackoffDelay(attempts + 1);
                Instant nextRetry = Instant.now().plusMillis(backoff);
                orderStateService.updateOrderItemFailure(
                        item.getOrderItemId(),
                        OrderItemStatus.RESERVED,
                        "RELEASE_TIMEOUT",
                        nextRetry,
                        attempts + 1
                );
            } catch (Exception e) {
                log.error("Unexpected error in compensation release for item {}: {}", item.getOrderItemReference(), e.getMessage());
            }
        }
    }

    /**
     * Pass 4: Reconcile AWAITING_PAYMENT orders that may have expired in Inventory Service.
     */
    public void reconcileExpiredAwaitingPayments() {
        List<Order> awaitingOrders = orderRepository.findByStatusInWithItems(
                List.of(OrderStatus.AWAITING_PAYMENT),
                PageRequest.of(0, batchSize)
        );

        Instant cutoff = Instant.now().minus(reservationTtlMinutes, ChronoUnit.MINUTES);

        for (Order order : awaitingOrders) {
            if (order.getUpdatedAt().isBefore(cutoff)) {
                for (OrderItem item : order.getItems()) {
                    if (item.getStatus() == OrderItemStatus.RESERVED && item.getReservationId() != null) {
                        try {
                            InventoryReservationDto remoteRes = inventoryReservationClient.getReservationById(item.getReservationId());
                            if (remoteRes.isExpired()) {
                                log.info("Detected remote reservation EXPIRED for item {} in order {}",
                                        item.getOrderItemReference(), order.getOrderReference());
                                orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.EXPIRED);
                                orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RESERVATION_EXPIRED);
                                orderService.compensateReservedItems(order);
                                break;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check reservation status for item {}: {}", item.getOrderItemReference(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public long calculateBackoffDelay(int attempt) {
        long delay = retryBaseDelayMs * (1L << Math.min(attempt - 1, 10));
        return Math.min(delay, retryMaxDelayMs);
    }
}
