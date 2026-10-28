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
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.*;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStateService orderStateService;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryReservationClient inventoryReservationClient;

    @InjectMocks
    private OrderService orderService;

    private final Long userId = 10L;
    private final String idempotencyKey = "test-idem-key-100";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "reservationTtlMinutes", 15);
        ReflectionTestUtils.setField(orderService, "retryBaseDelayMs", 5000L);
    }

    @Test
    @DisplayName("1. Single product, quantity 1 -> Creates order, reserves item, status AWAITING_PAYMENT (201)")
    void createOrder_SingleItem_Success() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        ProductDetailDto product = new ProductDetailDto(1, "Gold Ring", new BigDecimal("5000000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenReturn(product);

        when(orderStateService.createOrderWithItems(any(Order.class), anyList())).thenAnswer(inv -> {
            Order ord = inv.getArgument(0);
            List<OrderItem> items = inv.getArgument(1);
            ord.setOrderId(100L);
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setOrderItemId(200L + i);
                ord.addItem(items.get(i));
            }
            return ord;
        });

        InventoryReservationDto resDto = new InventoryReservationDto(
                501L, "ORD-REF", "ITEM-REF", 1, 1001L, "ACTIVE", 15,
                Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class))).thenReturn(resDto);
        when(orderStateService.checkAndCompleteOrderReservation(100L)).thenReturn(true);

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.order().totalAmount()).isEqualByComparingTo("5000000.00");
        assertThat(result.order().items()).hasSize(1);
        assertThat(result.order().items().get(0).status()).isEqualTo(OrderItemStatus.RESERVED);

        verify(orderStateService).checkAndCompleteOrderReservation(100L);
        verify(orderStateService).updateOrderItemSuccess(200L, 501L, 1001L, OrderItemStatus.RESERVED);
    }

    @Test
    @DisplayName("2. Quantity > 1 -> Expands into multiple physical items and reserves each individually")
    void createOrder_MultiplePhysicalItems_QuantityExpanded() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 3)));
        ProductDetailDto product = new ProductDetailDto(1, "Diamond Ring", new BigDecimal("3000000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenReturn(product);

        when(orderStateService.createOrderWithItems(any(Order.class), anyList())).thenAnswer(inv -> {
            Order ord = inv.getArgument(0);
            List<OrderItem> items = inv.getArgument(1);
            ord.setOrderId(100L);
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setOrderItemId(200L + i);
                ord.addItem(items.get(i));
            }
            return ord;
        });

        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class)))
                .thenReturn(new InventoryReservationDto(501L, "ORD", "ITEM-1", 1, 1001L, "ACTIVE", 15, Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()))
                .thenReturn(new InventoryReservationDto(502L, "ORD", "ITEM-2", 1, 1002L, "ACTIVE", 15, Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()))
                .thenReturn(new InventoryReservationDto(503L, "ORD", "ITEM-3", 1, 1003L, "ACTIVE", 15, Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()));

        when(orderStateService.checkAndCompleteOrderReservation(100L)).thenReturn(true);

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.order().items()).hasSize(3);
        assertThat(result.order().totalAmount()).isEqualByComparingTo("9000000.00");
        verify(inventoryReservationClient, times(3)).reserve(any());
    }

    @Test
    @DisplayName("3. Product not found -> Throws ProductNotFoundException without persisting order")
    void createOrder_ProductNotFound_Throws404() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(999, 1)));
        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(999)).thenThrow(new ProductNotFoundException(999));

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(orderStateService);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("4. Product inactive -> Throws ProductUnavailableException without persisting order")
    void createOrder_ProductInactive_Throws409() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(2, 1)));
        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(2)).thenThrow(new ProductUnavailableException(2, "Product inactive"));

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(ProductUnavailableException.class);

        verifyNoInteractions(orderStateService);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("5. Product Service outage -> Throws DownstreamServiceException without persisting order")
    void createOrder_ProductServiceOutage_Throws503() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenThrow(new DownstreamServiceException("product-service", 503, "Unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(DownstreamServiceException.class);

        verifyNoInteractions(orderStateService);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("6. Out of stock during reservation -> Fails remaining items and triggers compensation")
    void createOrder_OutOfStock_TriggersCompensation() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 2)));
        ProductDetailDto product = new ProductDetailDto(1, "Gold Ring", new BigDecimal("1000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenReturn(product);

        when(orderStateService.createOrderWithItems(any(Order.class), anyList())).thenAnswer(inv -> {
            Order ord = inv.getArgument(0);
            List<OrderItem> items = inv.getArgument(1);
            ord.setOrderId(100L);
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setOrderItemId(200L + i);
                ord.addItem(items.get(i));
            }
            return ord;
        });

        // 1st item succeeds, 2nd item throws ReservationConflictException (out of stock)
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class)))
                .thenReturn(new InventoryReservationDto(501L, "ORD", "ITEM-1", 1, 1001L, "ACTIVE", 15, Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()))
                .thenThrow(new ReservationConflictException("ORD", "ITEM-2", "No available inventory item"));

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(ProductUnavailableException.class);

        verify(orderStateService).failRemainingPendingItems(100L);
        verify(inventoryReservationClient).releaseReservation(501L);
        verify(orderStateService).updateOrderItemStatus(200L, OrderItemStatus.RELEASED);
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.RESERVATION_FAILED);
    }

    @Test
    @DisplayName("7. Inventory timeout -> Sets PENDING_RESERVATION with retry metadata and returns 202 ACCEPTED")
    void createOrder_InventoryTimeout_ReturnsAccepted() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        ProductDetailDto product = new ProductDetailDto(1, "Gold Ring", new BigDecimal("1000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenReturn(product);

        when(orderStateService.createOrderWithItems(any(Order.class), anyList())).thenAnswer(inv -> {
            Order ord = inv.getArgument(0);
            List<OrderItem> items = inv.getArgument(1);
            ord.setOrderId(100L);
            items.get(0).setOrderItemId(200L);
            ord.addItem(items.get(0));
            return ord;
        });

        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class)))
                .thenThrow(new DownstreamServiceException("inventory-service", "Timeout calling reserve", new RuntimeException()));

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isAccepted()).isTrue();
        verify(orderStateService).updateOrderItemFailure(eq(200L), eq(OrderItemStatus.PENDING_RESERVATION), eq("DOWNSTREAM_TIMEOUT"), any(), eq(1));
    }

    @Test
    @DisplayName("8. Reservation returns EXPIRED -> Marks EXPIRED and triggers compensation")
    void createOrder_ReservationReturnsExpired_CompensatesActive() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        ProductDetailDto product = new ProductDetailDto(1, "Gold Ring", new BigDecimal("1000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(productClient.getProductById(1)).thenReturn(product);

        when(orderStateService.createOrderWithItems(any(Order.class), anyList())).thenAnswer(inv -> {
            Order ord = inv.getArgument(0);
            List<OrderItem> items = inv.getArgument(1);
            ord.setOrderId(100L);
            items.get(0).setOrderItemId(200L);
            ord.addItem(items.get(0));
            return ord;
        });

        InventoryReservationDto expiredRes = new InventoryReservationDto(
                501L, "ORD", "ITEM-1", 1, 1001L, "EXPIRED", 15,
                Instant.now().minusSeconds(1000), Instant.now().minusSeconds(100), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class))).thenReturn(expiredRes);

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(ProductUnavailableException.class);

        verify(orderStateService).updateOrderItemStatus(200L, OrderItemStatus.EXPIRED);
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.RESERVATION_EXPIRED);
        verify(orderStateService).failRemainingPendingItems(100L);
    }

    @Test
    @DisplayName("9. Idempotent replay: same key and matching payload for AWAITING_PAYMENT -> Returns existing order (200)")
    void createOrder_IdempotentReplay_MatchingPayload_ReturnsExisting() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        Order existing = new Order("ORD-EXISTING", userId, new BigDecimal("5000000.00"), idempotencyKey);
        existing.setOrderId(50L);
        existing.setStatus(OrderStatus.AWAITING_PAYMENT);
        OrderItem item = new OrderItem("ORD-EXISTING-ITEM-1", 1, "Gold Ring", new BigDecimal("5000000.00"));
        item.setOrderItemId(250L);
        item.setStatus(OrderItemStatus.RESERVED);
        existing.addItem(item);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isExisting()).isTrue();
        assertThat(result.order().orderReference()).isEqualTo("ORD-EXISTING");
        assertThat(result.order().status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verifyNoInteractions(productClient);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("10. Idempotent replay for terminal RESERVATION_FAILED order -> Returns existing failed representation (200)")
    void createOrder_IdempotentReplay_FailedOrder_ReturnsExisting() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        Order existing = new Order("ORD-FAILED", userId, new BigDecimal("5000000.00"), idempotencyKey);
        existing.setOrderId(51L);
        existing.setStatus(OrderStatus.RESERVATION_FAILED);
        OrderItem item = new OrderItem("ORD-FAILED-ITEM-1", 1, "Gold Ring", new BigDecimal("5000000.00"));
        item.setOrderItemId(251L);
        item.setStatus(OrderItemStatus.RESERVATION_FAILED);
        existing.addItem(item);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isExisting()).isTrue();
        assertThat(result.order().orderReference()).isEqualTo("ORD-FAILED");
        assertThat(result.order().status()).isEqualTo(OrderStatus.RESERVATION_FAILED);
        verifyNoInteractions(productClient);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("11. Idempotent replay for PENDING_RESERVATION order -> Resumes reservation workflow")
    void createOrder_IdempotentReplay_PendingOrder_ResumesWorkflow() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));

        Order existing = new Order("ORD-PENDING", userId, new BigDecimal("5000000.00"), idempotencyKey);
        existing.setOrderId(52L);
        existing.setStatus(OrderStatus.PENDING_RESERVATION);
        OrderItem item = new OrderItem("ORD-PENDING-ITEM-1", 1, "Gold Ring", new BigDecimal("5000000.00"));
        item.setOrderItemId(252L);
        item.setStatus(OrderItemStatus.PENDING_RESERVATION);
        existing.addItem(item);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        InventoryReservationDto resDto = new InventoryReservationDto(
                505L, "ORD-PENDING", "ORD-PENDING-ITEM-1", 1, 1005L, "ACTIVE", 15,
                Instant.now(), Instant.now().plusSeconds(900), null, Instant.now(), Instant.now()
        );
        when(inventoryReservationClient.reserve(any(CreateReservationRequestDto.class))).thenReturn(resDto);
        when(orderStateService.checkAndCompleteOrderReservation(52L)).thenReturn(true);

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isCreated()).isTrue();
        verify(inventoryReservationClient).reserve(any());
        verify(orderStateService).updateOrderItemSuccess(252L, 505L, 1005L, OrderItemStatus.RESERVED);
    }

    @Test
    @DisplayName("12. Idempotency collision on unique index with winning order RESERVATION_FAILED -> Returns existing failed order")
    void createOrder_Collision_WinningOrderFailed_ReturnsExistingWithoutWorkflow() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1, 1)));
        ProductDetailDto product = new ProductDetailDto(1, "Gold Ring", new BigDecimal("1000.00"), true);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty()) // Initial check misses due to race
                .thenReturn(Optional.of(new Order("ORD-WINNING", userId, new BigDecimal("1000.00"), idempotencyKey) {{
                    setOrderId(999L);
                    setStatus(OrderStatus.RESERVATION_FAILED);
                    addItem(new OrderItem("ORD-WINNING-1", 1, "Gold Ring", new BigDecimal("1000.00")));
                }})); // Second check finds winning order

        when(productClient.getProductById(1)).thenReturn(product);
        when(orderStateService.createOrderWithItems(any(), any())).thenThrow(new DataIntegrityViolationException("Unique key violation"));

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);

        assertThat(result.isExisting()).isTrue();
        assertThat(result.order().orderReference()).isEqualTo("ORD-WINNING");
        assertThat(result.order().status()).isEqualTo(OrderStatus.RESERVATION_FAILED);
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("13. Idempotency conflict: same key but different product -> Throws 409 IdempotencyConflictException")
    void createOrder_IdempotentReplay_DifferentPayload_Throws409() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(2, 1)));

        Order existing = new Order("ORD-EXISTING", userId, new BigDecimal("5000000.00"), idempotencyKey);
        existing.setOrderId(50L);
        OrderItem item = new OrderItem("ORD-EXISTING-ITEM-1", 1, "Gold Ring", new BigDecimal("5000000.00"));
        existing.addItem(item);

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.createOrder(userId, idempotencyKey, request))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("14. Idempotency payload comparison: items in different order are treated as equivalent")
    void createOrder_IdempotentReplay_ShuffledItems_Equivalent() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(2, 1),
                new OrderItemRequest(1, 2)
        ));

        Order existing = new Order("ORD-EXISTING-MULTI", userId, new BigDecimal("7000.00"), idempotencyKey);
        existing.setOrderId(55L);
        existing.setStatus(OrderStatus.AWAITING_PAYMENT);
        existing.addItem(new OrderItem("ITEM-1", 1, "Ring 1", new BigDecimal("2000.00")));
        existing.addItem(new OrderItem("ITEM-2", 1, "Ring 1", new BigDecimal("2000.00")));
        existing.addItem(new OrderItem("ITEM-3", 2, "Ring 2", new BigDecimal("3000.00")));

        when(orderRepository.findWithItemsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        CreateOrderResult result = orderService.createOrder(userId, idempotencyKey, request);
        assertThat(result.isExisting()).isTrue();
        assertThat(result.order().orderReference()).isEqualTo("ORD-EXISTING-MULTI");
    }

    @Test
    @DisplayName("15. Cancel order in AWAITING_PAYMENT: all reservations released -> CANCELLED (200)")
    void cancelOrder_Success_AllReleased() {
        Order order = new Order("ORD-CANCEL", userId, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        OrderItem item1 = new OrderItem("ITEM-1", 1, "Ring", new BigDecimal("5000.00"));
        item1.setOrderItemId(201L);
        item1.setStatus(OrderItemStatus.RESERVED);
        item1.setReservationId(901L);
        order.addItem(item1);

        when(orderRepository.findWithItemsByOrderReference("ORD-CANCEL")).thenReturn(Optional.of(order));

        when(inventoryReservationClient.releaseReservation(901L))
                .thenReturn(new InventoryReservationDto(901L, "ORD-CANCEL", "ITEM-1", 1, 1001L, "RELEASED", 15, Instant.now(), Instant.now(), Instant.now(), Instant.now(), Instant.now()));

        CancelOrderResult result = orderService.cancelOrder("ORD-CANCEL", userId, false);

        assertThat(result.fullyCancelled()).isTrue();
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.CANCELLATION_PENDING);
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.CANCELLED);
        verify(orderStateService).updateOrderItemStatus(201L, OrderItemStatus.RELEASED);
    }

    @Test
    @DisplayName("16. Cancel order already CANCELLED -> Returns existing without duplicate release calls")
    void cancelOrder_AlreadyCancelled() {
        Order order = new Order("ORD-ALREADY", userId, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.CANCELLED);

        when(orderRepository.findWithItemsByOrderReference("ORD-ALREADY")).thenReturn(Optional.of(order));

        CancelOrderResult result = orderService.cancelOrder("ORD-ALREADY", userId, false);

        assertThat(result.fullyCancelled()).isTrue();
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    @DisplayName("17. Cancel order in CONFIRMED -> Throws OrderConflictException (409)")
    void cancelOrder_Confirmed_ThrowsConflict() {
        Order order = new Order("ORD-CONFIRMED", userId, new BigDecimal("5000.00"), "idem-1");
        order.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findWithItemsByOrderReference("ORD-CONFIRMED")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-CONFIRMED", userId, false))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("18. Cancel order in PENDING_RESERVATION -> Throws OrderConflictException (409)")
    void cancelOrder_PendingReservation_ThrowsConflict() {
        Order order = new Order("ORD-PENDING", userId, new BigDecimal("5000.00"), "idem-1");
        order.setStatus(OrderStatus.PENDING_RESERVATION);

        when(orderRepository.findWithItemsByOrderReference("ORD-PENDING")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-PENDING", userId, false))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @DisplayName("19. Cancel order with transient timeout on release -> Remains CANCELLATION_PENDING (202)")
    void cancelOrder_ReleaseTimeout_ReturnsAccepted() {
        Order order = new Order("ORD-TIMEOUT", userId, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        OrderItem item = new OrderItem("ITEM-1", 1, "Ring", new BigDecimal("5000.00"));
        item.setOrderItemId(201L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(901L);
        order.addItem(item);

        when(orderRepository.findWithItemsByOrderReference("ORD-TIMEOUT")).thenReturn(Optional.of(order));

        when(inventoryReservationClient.releaseReservation(901L))
                .thenThrow(new DownstreamServiceException("inventory-service", "Timeout calling release", new RuntimeException()));

        CancelOrderResult result = orderService.cancelOrder("ORD-TIMEOUT", userId, false);

        assertThat(result.fullyCancelled()).isFalse();
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.CANCELLATION_PENDING);
        verify(orderStateService, never()).updateOrderStatus(100L, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("20. Release 409 conflict: GET reveals EXPIRED -> Marks EXPIRED and completes cancellation")
    void cancelOrder_ReleaseConflict_RemoteExpired_Completes() {
        Order order = new Order("ORD-EXPIRED", userId, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        OrderItem item = new OrderItem("ITEM-1", 1, "Ring", new BigDecimal("5000.00"));
        item.setOrderItemId(201L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(901L);
        order.addItem(item);

        when(orderRepository.findWithItemsByOrderReference("ORD-EXPIRED")).thenReturn(Optional.of(order));

        when(inventoryReservationClient.releaseReservation(901L))
                .thenThrow(new ReservationConflictException("ORD-EXPIRED", "ITEM-1", "Cannot release expired reservation"));

        when(inventoryReservationClient.getReservationById(901L))
                .thenReturn(new InventoryReservationDto(901L, "ORD-EXPIRED", "ITEM-1", 1, 1001L, "EXPIRED", 15, Instant.now().minusSeconds(1000), Instant.now().minusSeconds(100), null, Instant.now(), Instant.now()));

        CancelOrderResult result = orderService.cancelOrder("ORD-EXPIRED", userId, false);

        assertThat(result.fullyCancelled()).isTrue();
        verify(orderStateService).updateOrderItemStatus(201L, OrderItemStatus.EXPIRED);
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("21. Release 409 conflict: GET reveals COMMITTED -> Marks RECONCILIATION_REQUIRED")
    void cancelOrder_ReleaseConflict_RemoteCommitted_Reconciliation() {
        Order order = new Order("ORD-COMMITTED", userId, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        OrderItem item = new OrderItem("ITEM-1", 1, "Ring", new BigDecimal("5000.00"));
        item.setOrderItemId(201L);
        item.setStatus(OrderItemStatus.RESERVED);
        item.setReservationId(901L);
        order.addItem(item);

        when(orderRepository.findWithItemsByOrderReference("ORD-COMMITTED")).thenReturn(Optional.of(order));

        when(inventoryReservationClient.releaseReservation(901L))
                .thenThrow(new ReservationConflictException("ORD-COMMITTED", "ITEM-1", "Cannot release committed reservation"));

        when(inventoryReservationClient.getReservationById(901L))
                .thenReturn(new InventoryReservationDto(901L, "ORD-COMMITTED", "ITEM-1", 1, 1001L, "COMMITTED", 15, Instant.now().minusSeconds(1000), Instant.now().plusSeconds(900), Instant.now(), Instant.now(), Instant.now()));

        CancelOrderResult result = orderService.cancelOrder("ORD-COMMITTED", userId, false);

        assertThat(result.fullyCancelled()).isFalse();
        verify(orderStateService).updateOrderStatus(100L, OrderStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("22. Non-owner USER attempts cancellation -> Throws OrderAccessDeniedException (403)")
    void cancelOrder_NonOwner_ThrowsForbidden() {
        Order order = new Order("ORD-OTHER", 999L, new BigDecimal("5000.00"), "idem-1");
        when(orderRepository.findWithItemsByOrderReference("ORD-OTHER")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-OTHER", userId, false))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    @DisplayName("23. ADMIN cancels any user's order -> Permitted")
    void cancelOrder_AdminOverride_Permitted() {
        Order order = new Order("ORD-OTHER", 999L, new BigDecimal("5000.00"), "idem-1");
        order.setOrderId(100L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        when(orderRepository.findWithItemsByOrderReference("ORD-OTHER")).thenReturn(Optional.of(order));

        CancelOrderResult result = orderService.cancelOrder("ORD-OTHER", userId, true);
        assertThat(result.fullyCancelled()).isTrue();
    }

    @Test
    @DisplayName("24. Owner retrieves order -> Success (200)")
    void getOrderByReference_Owner_Success() {
        Order order = new Order("ORD-1", userId, new BigDecimal("1000.00"), "key");
        when(orderRepository.findWithItemsByOrderReference("ORD-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderByReference("ORD-1", userId, false);
        assertThat(response.orderReference()).isEqualTo("ORD-1");
    }

    @Test
    @DisplayName("25. Non-owner retrieves order -> Throws OrderAccessDeniedException (403)")
    void getOrderByReference_NonOwner_ThrowsForbidden() {
        Order order = new Order("ORD-1", 999L, new BigDecimal("1000.00"), "key");
        when(orderRepository.findWithItemsByOrderReference("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderByReference("ORD-1", userId, false))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    @DisplayName("26. ADMIN retrieves any order -> Success (200)")
    void getOrderByReference_Admin_Success() {
        Order order = new Order("ORD-1", 999L, new BigDecimal("1000.00"), "key");
        when(orderRepository.findWithItemsByOrderReference("ORD-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderByReference("ORD-1", userId, true);
        assertThat(response.orderReference()).isEqualTo("ORD-1");
    }
}
