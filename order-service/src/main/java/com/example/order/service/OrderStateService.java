package com.example.order.service;

import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrderStateService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderStateService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createOrderWithItems(Order order, List<OrderItem> items) {
        Order savedOrder = orderRepository.save(order);
        for (OrderItem item : items) {
            savedOrder.addItem(item);
        }
        return orderRepository.saveAndFlush(savedOrder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setUpdatedAt(Instant.now());
            orderRepository.saveAndFlush(order);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean checkAndCompleteOrderReservation(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null) {
            return false;
        }

        // Strict state guard: Only PENDING_RESERVATION orders can transition to AWAITING_PAYMENT
        if (order.getStatus() != OrderStatus.PENDING_RESERVATION) {
            log.info("Order {} is in status {}, not eligible for AWAITING_PAYMENT transition",
                    order.getOrderReference(), order.getStatus());
            return false;
        }

        boolean allReserved = order.getItems().stream()
                .allMatch(i -> i.getStatus() == OrderItemStatus.RESERVED);

        if (allReserved) {
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            order.setUpdatedAt(Instant.now());
            orderRepository.saveAndFlush(order);
            log.info("Order {} transitioned to AWAITING_PAYMENT in isolated transaction", order.getOrderReference());
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failRemainingPendingItems(Long orderId) {
        int updated = orderItemRepository.updateStatusForRemainingItems(
                orderId,
                OrderItemStatus.PENDING_RESERVATION,
                OrderItemStatus.RESERVATION_FAILED,
                "ORDER_FAILED_UNPROCESSED",
                Instant.now()
        );
        log.info("Marked {} remaining pending items as RESERVATION_FAILED for orderId={}", updated, orderId);
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderItemSuccess(
            Long itemId,
            Long reservationId,
            Long inventoryItemId,
            OrderItemStatus status
    ) {
        orderItemRepository.findById(itemId).ifPresent(item -> {
            item.setReservationId(reservationId);
            item.setInventoryItemId(inventoryItemId);
            item.setStatus(status);
            item.setLastErrorCode(null);
            item.setNextRetryAt(null);
            item.setUpdatedAt(Instant.now());
            orderItemRepository.saveAndFlush(item);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderItemStatus(Long itemId, OrderItemStatus status) {
        orderItemRepository.findById(itemId).ifPresent(item -> {
            item.setStatus(status);
            item.setUpdatedAt(Instant.now());
            orderItemRepository.saveAndFlush(item);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderItemFailure(
            Long itemId,
            OrderItemStatus status,
            String errorCode,
            Instant nextRetryAt,
            int retryCount
    ) {
        orderItemRepository.findById(itemId).ifPresent(item -> {
            item.setStatus(status);
            item.setLastErrorCode(errorCode);
            item.setNextRetryAt(nextRetryAt);
            item.setRetryCount(retryCount);
            item.setUpdatedAt(Instant.now());
            orderItemRepository.saveAndFlush(item);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderAndItemStatus(
            Long orderId,
            OrderStatus orderStatus,
            Long itemId,
            OrderItemStatus itemStatus
    ) {
        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setStatus(orderStatus);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
            });
        }
        if (itemId != null) {
            orderItemRepository.findById(itemId).ifPresent(item -> {
                item.setStatus(itemStatus);
                item.setUpdatedAt(Instant.now());
                orderItemRepository.save(item);
            });
        }
        orderRepository.flush();
    }
}
