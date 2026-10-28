package com.example.order.service;

import com.example.order.client.InventoryReservationClient;
import com.example.order.client.ProductClient;
import com.example.order.dto.external.CreateReservationRequestDto;
import com.example.order.dto.external.InventoryReservationDto;
import com.example.order.dto.external.ProductDetailDto;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.request.OrderItemRequest;
import com.example.order.dto.response.CancelOrderResult;
import com.example.order.dto.response.CreateOrderResult;
import com.example.order.dto.response.OrderItemResponse;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.*;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final ProductClient productClient;
    private final InventoryReservationClient inventoryReservationClient;

    @Value("${app.order.reservation-ttl-minutes:15}")
    private int reservationTtlMinutes;

    @Value("${app.order.retry-base-delay-ms:5000}")
    private long retryBaseDelayMs;

    public OrderService(
            OrderRepository orderRepository,
            OrderStateService orderStateService,
            ProductClient productClient,
            InventoryReservationClient inventoryReservationClient
    ) {
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.productClient = productClient;
        this.inventoryReservationClient = inventoryReservationClient;
    }

    public CreateOrderResult createOrder(Long userId, String idempotencyKey, CreateOrderRequest request) {
        Map<Integer, Integer> requestedPayload = request.toNormalizedPayload();

        // 1. Check idempotency pre-check
        Optional<Order> existingOpt = orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOpt.isPresent()) {
            Order existing = existingOpt.get();
            validatePayloadMatch(existing, requestedPayload, idempotencyKey);

            if (existing.getStatus() == OrderStatus.PENDING_RESERVATION) {
                log.info("Resuming reservation workflow for pending order {}", existing.getOrderReference());
                return executeReservationWorkflow(existing);
            }

            // For ALL other states (AWAITING_PAYMENT, CONFIRMED, CANCELLED, CANCELLATION_PENDING,
            // RESERVATION_FAILED, RESERVATION_EXPIRED, RECONCILIATION_REQUIRED):
            // Return stable idempotent replay: Status.EXISTING (HTTP 200 OK)
            log.info("Idempotent replay for order {} with existing status {}", existing.getOrderReference(), existing.getStatus());
            return new CreateOrderResult(toResponse(existing), CreateOrderResult.Status.EXISTING);
        }

        // 2. Query Product Service before creating Order
        Set<Integer> uniqueProductIds = requestedPayload.keySet();
        Map<Integer, ProductDetailDto> productSnapshots = new HashMap<>();
        for (Integer productId : uniqueProductIds) {
            ProductDetailDto product = productClient.getProductById(productId);
            productSnapshots.put(productId, product);
        }

        // 3. Build Order and OrderItems entities
        String orderReference = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int itemIndex = 1;

        for (OrderItemRequest reqItem : request.getItems()) {
            ProductDetailDto product = productSnapshots.get(reqItem.getProductId());
            for (int i = 0; i < reqItem.getQuantity(); i++) {
                String itemRef = orderReference + "-ITEM-" + itemIndex++;
                OrderItem orderItem = new OrderItem(
                        itemRef,
                        product.productId(),
                        product.productName(),
                        product.price()
                );
                items.add(orderItem);
                totalAmount = totalAmount.add(product.price());
            }
        }

        Order order = new Order(orderReference, userId, totalAmount, idempotencyKey);

        // 4. Persist initial order state in isolated transaction with collision safety
        Order persistedOrder;
        try {
            persistedOrder = orderStateService.createOrderWithItems(order, items);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent collision on unique index UX_orders_user_idempotency_key
            log.warn("Concurrent duplicate collision for userId={}, idempotencyKey={}. Re-reading winning record.", userId, idempotencyKey);
            Order winning = orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            validatePayloadMatch(winning, requestedPayload, idempotencyKey);

            if (winning.getStatus() == OrderStatus.PENDING_RESERVATION) {
                return executeReservationWorkflow(winning);
            }
            // Winning order in terminal or non-pending state is never passed to reservation workflow
            return new CreateOrderResult(toResponse(winning), CreateOrderResult.Status.EXISTING);
        }

        // 5. Execute reservation workflow
        return executeReservationWorkflow(persistedOrder);
    }

    private CreateOrderResult executeReservationWorkflow(Order order) {
        List<OrderItem> items = order.getItems();
        boolean hasPendingRetries = false;

        for (OrderItem item : items) {
            if (item.getStatus() == OrderItemStatus.RESERVED) {
                continue; // Already reserved
            }

            if (item.getStatus() == OrderItemStatus.PENDING_RESERVATION) {
                try {
                    CreateReservationRequestDto reserveReq = new CreateReservationRequestDto(
                            order.getOrderReference(),
                            item.getOrderItemReference(),
                            item.getProductId(),
                            reservationTtlMinutes
                    );

                    InventoryReservationDto reservation = inventoryReservationClient.reserve(reserveReq);

                    if (reservation == null) {
                        log.error("Null reservation response for item {}", item.getOrderItemReference());
                        orderStateService.updateOrderItemFailure(
                                item.getOrderItemId(),
                                OrderItemStatus.RECONCILIATION_REQUIRED,
                                "NULL_RESERVATION_RESPONSE",
                                null,
                                (item.getRetryCount() != null ? item.getRetryCount() : 0) + 1
                        );
                        orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        orderStateService.failRemainingPendingItems(order.getOrderId());
                        throw new OrderConflictException("Null reservation response from inventory");
                    }

                    if (reservation.isActive()) {
                        orderStateService.updateOrderItemSuccess(
                                item.getOrderItemId(),
                                reservation.reservationId(),
                                reservation.inventoryItemId(),
                                OrderItemStatus.RESERVED
                        );
                        item.setStatus(OrderItemStatus.RESERVED);
                        item.setReservationId(reservation.reservationId());
                        item.setInventoryItemId(reservation.inventoryItemId());
                    } else if (reservation.isExpired()) {
                        log.warn("Reservation returned EXPIRED for item {}", item.getOrderItemReference());
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.EXPIRED);
                        orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RESERVATION_EXPIRED);
                        orderStateService.failRemainingPendingItems(order.getOrderId());
                        item.setStatus(OrderItemStatus.EXPIRED);
                        compensateReservedItems(order);
                        throw new ProductUnavailableException(item.getProductId(), "Reservation expired during creation");
                    } else if (reservation.isCommitted()) {
                        log.error("Unexpected COMMITTED status from inventory for item {}", item.getOrderItemReference());
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.COMMITTED);
                        orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        orderStateService.failRemainingPendingItems(order.getOrderId());
                        throw new OrderConflictException("Unexpected committed reservation state from inventory");
                    } else {
                        log.error("Unrecognized reservation status '{}' for item {}", reservation.status(), item.getOrderItemReference());
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                        orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        orderStateService.failRemainingPendingItems(order.getOrderId());
                        throw new OrderConflictException("Unrecognized reservation status: " + reservation.status());
                    }
                } catch (ReservationConflictException ex) {
                    log.warn("Reservation definitive conflict for item {}: {}", item.getOrderItemReference(), ex.getMessage());
                    orderStateService.updateOrderItemFailure(
                            item.getOrderItemId(),
                            OrderItemStatus.RESERVATION_FAILED,
                            "OUT_OF_STOCK",
                            null,
                            (item.getRetryCount() != null ? item.getRetryCount() : 0) + 1
                    );
                    item.setStatus(OrderItemStatus.RESERVATION_FAILED);
                    orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RESERVATION_FAILED);
                    order.setStatus(OrderStatus.RESERVATION_FAILED);

                    // Ensure remaining unprocessed items cannot later be reserved by scheduler
                    orderStateService.failRemainingPendingItems(order.getOrderId());
                    for (OrderItem other : items) {
                        if (other.getStatus() == OrderItemStatus.PENDING_RESERVATION) {
                            other.setStatus(OrderItemStatus.RESERVATION_FAILED);
                        }
                    }

                    compensateReservedItems(order);
                    throw new ProductUnavailableException(item.getProductId(), "Item unavailable or out of stock: " + ex.getMessage());
                } catch (DownstreamServiceException ex) {
                    log.warn("Reservation unknown outcome (timeout/network) for item {}: {}", item.getOrderItemReference(), ex.getMessage());
                    Instant nextRetry = Instant.now().plusMillis(retryBaseDelayMs);
                    orderStateService.updateOrderItemFailure(
                            item.getOrderItemId(),
                            OrderItemStatus.PENDING_RESERVATION,
                            "DOWNSTREAM_TIMEOUT",
                            nextRetry,
                            (item.getRetryCount() != null ? item.getRetryCount() : 0) + 1
                    );
                    hasPendingRetries = true;
                    break;
                }
            }
        }

        if (hasPendingRetries) {
            Order reloaded = orderRepository.findWithItemsByOrderReference(order.getOrderReference()).orElse(order);
            return new CreateOrderResult(toResponse(reloaded), CreateOrderResult.Status.ACCEPTED);
        }

        // Complete order transition with isolated transactional check
        boolean completed = orderStateService.checkAndCompleteOrderReservation(order.getOrderId());
        Order reloaded = orderRepository.findWithItemsByOrderReference(order.getOrderReference()).orElse(order);

        if (completed || reloaded.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            return new CreateOrderResult(toResponse(reloaded), CreateOrderResult.Status.CREATED);
        }

        return new CreateOrderResult(toResponse(reloaded), CreateOrderResult.Status.ACCEPTED);
    }

    public void compensateReservedItems(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() == OrderItemStatus.RESERVED && item.getReservationId() != null) {
                try {
                    inventoryReservationClient.releaseReservation(item.getReservationId());
                    orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RELEASED);
                    item.setStatus(OrderItemStatus.RELEASED);
                } catch (ReservationConflictException ex) {
                    // Check remote status
                    try {
                        InventoryReservationDto current = inventoryReservationClient.getReservationById(item.getReservationId());
                        if (current.isExpired()) {
                            orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.EXPIRED);
                            item.setStatus(OrderItemStatus.EXPIRED);
                        } else if (current.isReleased()) {
                            orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RELEASED);
                            item.setStatus(OrderItemStatus.RELEASED);
                        } else if (current.isCommitted()) {
                            orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.COMMITTED);
                            orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                            order.setStatus(OrderStatus.RECONCILIATION_REQUIRED);
                            item.setStatus(OrderItemStatus.COMMITTED);
                        } else {
                            orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                            orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                            order.setStatus(OrderStatus.RECONCILIATION_REQUIRED);
                            item.setStatus(OrderItemStatus.RECONCILIATION_REQUIRED);
                        }
                    } catch (Exception inspectEx) {
                        log.error("Failed to inspect reservation status during compensation for item {}: {}",
                                item.getOrderItemReference(), inspectEx.getMessage());
                        orderStateService.updateOrderItemStatus(item.getOrderItemId(), OrderItemStatus.RECONCILIATION_REQUIRED);
                        orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
                        order.setStatus(OrderStatus.RECONCILIATION_REQUIRED);
                    }
                } catch (DownstreamServiceException ex) {
                    log.warn("Downstream failure during release for item {}: {}", item.getOrderItemReference(), ex.getMessage());
                    Instant nextRetry = Instant.now().plusMillis(retryBaseDelayMs);
                    orderStateService.updateOrderItemFailure(
                            item.getOrderItemId(),
                            OrderItemStatus.RESERVED,
                            "RELEASE_TIMEOUT",
                            nextRetry,
                            (item.getRetryCount() != null ? item.getRetryCount() : 0) + 1
                    );
                }
            }
        }
    }

    public CancelOrderResult cancelOrder(String orderReference, Long userId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsByOrderReference(orderReference)
                .orElseThrow(() -> new OrderNotFoundException(orderReference));

        validateOwnership(order, userId, isAdmin);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new CancelOrderResult(toResponse(order), true);
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new OrderConflictException("Cannot cancel confirmed order in current scope");
        }

        if (order.getStatus() == OrderStatus.PENDING_RESERVATION) {
            throw new OrderConflictException("Cannot cancel order while reservation workflow is still pending");
        }

        if (order.getStatus() == OrderStatus.RECONCILIATION_REQUIRED) {
            throw new OrderConflictException("Cannot cancel order requiring reconciliation");
        }

        // Transition to CANCELLATION_PENDING before remote releases
        if (order.getStatus() != OrderStatus.CANCELLATION_PENDING) {
            orderStateService.updateOrderStatus(order.getOrderId(), OrderStatus.CANCELLATION_PENDING);
            order.setStatus(OrderStatus.CANCELLATION_PENDING);
        }

        // Release reserved items
        compensateReservedItems(order);

        Order reloaded = orderRepository.findWithItemsByOrderReference(orderReference).orElse(order);

        boolean anyPending = reloaded.getItems().stream()
                .anyMatch(i -> i.getStatus() == OrderItemStatus.RESERVED || i.getStatus() == OrderItemStatus.PENDING_RESERVATION);

        boolean anyReconciliation = reloaded.getStatus() == OrderStatus.RECONCILIATION_REQUIRED ||
                reloaded.getItems().stream()
                        .anyMatch(i -> i.getStatus() == OrderItemStatus.RECONCILIATION_REQUIRED || i.getStatus() == OrderItemStatus.COMMITTED);

        if (anyReconciliation) {
            if (reloaded.getStatus() != OrderStatus.RECONCILIATION_REQUIRED) {
                orderStateService.updateOrderStatus(reloaded.getOrderId(), OrderStatus.RECONCILIATION_REQUIRED);
            }
            reloaded.setStatus(OrderStatus.RECONCILIATION_REQUIRED);
            return new CancelOrderResult(toResponse(reloaded), false);
        }

        if (anyPending) {
            // Temporary release failure, scheduler will retry
            return new CancelOrderResult(toResponse(reloaded), false);
        }

        // All items released/expired/failed -> fully cancelled
        orderStateService.updateOrderStatus(reloaded.getOrderId(), OrderStatus.CANCELLED);
        reloaded.setStatus(OrderStatus.CANCELLED);
        return new CancelOrderResult(toResponse(reloaded), true);
    }

    public OrderResponse getOrderByReference(String orderReference, Long userId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsByOrderReference(orderReference)
                .orElseThrow(() -> new OrderNotFoundException(orderReference));

        validateOwnership(order, userId, isAdmin);
        return toResponse(order);
    }

    public Page<OrderResponse> listOrders(Long userId, boolean isAdmin, OrderStatus status, Pageable pageable) {
        Page<Order> orderPage;
        if (isAdmin) {
            orderPage = (status != null)
                    ? orderRepository.findByStatus(status, pageable)
                    : orderRepository.findAll(pageable);
        } else {
            orderPage = (status != null)
                    ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                    : orderRepository.findByUserId(userId, pageable);
        }
        return orderPage.map(this::toResponse);
    }

    private void validateOwnership(Order order, Long userId, boolean isAdmin) {
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("User does not have permission to access order " + order.getOrderReference());
        }
    }

    private void validatePayloadMatch(Order existing, Map<Integer, Integer> requestedPayload, String idempotencyKey) {
        Map<Integer, Integer> existingPayload = new HashMap<>();
        for (OrderItem item : existing.getItems()) {
            existingPayload.merge(item.getProductId(), 1, Integer::sum);
        }

        if (!existingPayload.equals(requestedPayload)) {
            log.warn("Idempotency conflict for order {}: payload mismatch. Existing: {}, Requested: {}",
                    existing.getOrderReference(), existingPayload, requestedPayload);
            throw new IdempotencyConflictException(idempotencyKey);
        }
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getOrderItemId(),
                        i.getOrderItemReference(),
                        i.getProductId(),
                        i.getProductNameSnapshot(),
                        i.getUnitPrice(),
                        i.getReservationId(),
                        i.getInventoryItemId(),
                        i.getStatus(),
                        i.getRetryCount(),
                        i.getCreatedAt(),
                        i.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getOrderId(),
                order.getOrderReference(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getIdempotencyKey(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemResponses
        );
    }
}
