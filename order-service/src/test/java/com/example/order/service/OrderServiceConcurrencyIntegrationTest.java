package com.example.order.service;

import com.example.order.client.InventoryReservationClient;
import com.example.order.client.ProductClient;
import com.example.order.dto.external.CreateReservationRequestDto;
import com.example.order.dto.external.InventoryReservationDto;
import com.example.order.dto.external.ProductDetailDto;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.request.OrderItemRequest;
import com.example.order.dto.response.CreateOrderResult;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderServiceConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private InventoryReservationClient inventoryReservationClient;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long orderId : createdOrderIds) {
            try {
                orderRepository.findById(orderId).ifPresent(orderRepository::delete);
            } catch (Exception ignored) {
            }
        }
        createdOrderIds.clear();
    }

    @Test
    @DisplayName("1. Service-level concurrency: 4 concurrent threads invoking createOrder with same user and Idempotency-Key -> All complete safely, returning identical order")
    void concurrentServiceCalls_SameUserAndIdempotencyKey_AllSucceedWithSameOrder() throws Exception {
        int threadCount = 4;
        Long userId = 888L;
        String sharedKey = "CONCURRENT-SVC-" + UUID.randomUUID();

        when(productClient.getProductById(1))
                .thenReturn(new ProductDetailDto(1, "Diamond Ring", new BigDecimal("7500000.00"), true));

        AtomicInteger reservationSeq = new AtomicInteger(5000);
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class)))
                .thenAnswer(invocation -> {
                    CreateReservationRequestDto req = invocation.getArgument(0);
                    long resId = reservationSeq.incrementAndGet();
                    return new InventoryReservationDto(
                            resId,
                            req.orderReference(),
                            req.orderItemReference(),
                            req.productId(),
                            10000L + resId,
                            "ACTIVE",
                            req.ttlMinutes(),
                            Instant.now(),
                            Instant.now().plusSeconds(req.ttlMinutes() * 60L),
                            null,
                            null,
                            null
                    );
                });

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<CreateOrderResult> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    CreateOrderResult res = orderService.createOrder(userId, sharedKey, request);
                    results.add(res);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(errors).as("No threads should encounter unhandled exception").isEmpty();
        assertThat(results).as("All 4 threads should receive an order result").hasSize(threadCount);

        // Verify all threads received the exact same order reference and order id
        String expectedOrderRef = results.get(0).order().orderReference();
        Long expectedOrderId = results.get(0).order().orderId();
        createdOrderIds.add(expectedOrderId);

        for (CreateOrderResult res : results) {
            assertThat(res.order().orderReference()).isEqualTo(expectedOrderRef);
            assertThat(res.order().orderId()).isEqualTo(expectedOrderId);
            assertThat(res.order().userId()).isEqualTo(userId);
            assertThat(res.order().idempotencyKey()).isEqualTo(sharedKey);
        }

        // Verify only 1 order row exists in the database
        long matchingOrders = orderRepository.findAll().stream()
                .filter(o -> userId.equals(o.getUserId()) && sharedKey.equals(o.getIdempotencyKey()))
                .count();
        assertThat(matchingOrders).isEqualTo(1L);
    }

    @Test
    @DisplayName("2. Service-level concurrency: 4 concurrent replay calls on an existing FAILED order -> All return EXISTING without re-reserving")
    void concurrentServiceCalls_ExistingFailedOrder_AllReturnExistingWithoutReExecution() throws Exception {
        int threadCount = 4;
        Long userId = 889L;
        String sharedKey = "CONCURRENT-SVC-FAILED-" + UUID.randomUUID();

        // Seed an existing failed order directly in the database
        Order failedOrder = new Order("ORD-SEEDED-FAILED-" + UUID.randomUUID().toString().substring(0, 8),
                userId, new BigDecimal("7500000.00"), sharedKey);
        failedOrder.setStatus(OrderStatus.RESERVATION_FAILED);
        OrderItem item = new OrderItem(failedOrder.getOrderReference() + "-ITEM-1", 1, "Diamond Ring", new BigDecimal("7500000.00"));
        item.setStatus(OrderItemStatus.RESERVATION_FAILED);
        failedOrder.addItem(item);
        failedOrder = orderRepository.saveAndFlush(failedOrder);
        createdOrderIds.add(failedOrder.getOrderId());

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<CreateOrderResult> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    CreateOrderResult res = orderService.createOrder(userId, sharedKey, request);
                    results.add(res);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();
        assertThat(results).hasSize(threadCount);

        for (CreateOrderResult res : results) {
            assertThat(res.status()).isEqualTo(CreateOrderResult.Status.EXISTING);
            assertThat(res.order().orderReference()).isEqualTo(failedOrder.getOrderReference());
            assertThat(res.order().status()).isEqualTo(OrderStatus.RESERVATION_FAILED);
        }

        // Verify downstream reservation client was NEVER called
        verify(inventoryReservationClient, never()).reserve(any());
    }
}
