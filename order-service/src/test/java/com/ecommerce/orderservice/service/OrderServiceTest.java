package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.domain.OutboxMessage;
import com.ecommerce.orderservice.domain.OutboxStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.grpc.InventoryGrpcClient;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        orderService = new OrderService(
                orderRepository,
                outboxMessageRepository,
                inventoryGrpcClient,
                objectMapper
        );
    }

    @Test
    @DisplayName("Успешное создание заказа: резервирование товара по gRPC и реализация Transctional Outbox Pattern")
    void createOrderShouldReserveStockAndSaveOrderAndOutboxWhenStockAvailable() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(
                        "SKU-100", 100, new BigDecimal("200.00")
                )));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(response.totalAmount()).isEqualByComparingTo("20000.00");
        assertThat(response.items()).hasSize(1);

        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());

        OutboxMessage savedOutboxMessage = outboxCaptor.getValue();
        assertThat(savedOutboxMessage.getTopic()).isEqualTo(OrderService.ORDER_CREATED_TOPIC);
        assertThat(savedOutboxMessage.getAggregateId()).isEqualTo(response.id().toString());
        assertThat(savedOutboxMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Неуспешное создание заказа: пометка статуса заказа CANCELLED")
    void createOrderShouldCancelOrderWhenOutboxIsCancelled() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(
                        "SKU-100", 100, new BigDecimal("200.00")
                )));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Insufficient stock or unavailable");
        verify(outboxMessageRepository, never()).save(any(OutboxMessage.class));
    }

    @Test
    @DisplayName("Отмена заказа: возвращение кол-ва товара и сохранения нового статуса в Outbox")
    void cancelOrderShouldReleaseStockAndSaveOutboxWhenOrderIsReserved() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order existingOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.RESERVED)
                .totalAmount(new BigDecimal("200.00"))
                .build();

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId, userId, null);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Cancelled by user");

        verify(inventoryGrpcClient).releaseStock(orderId.toString());

        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());

        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo(OrderService.ORDER_STATUS_CHANGED_TOPIC);
    }

    @Test
    @DisplayName("Отмена заказа: выброс ошибки NoSuchElementException если товар не найден")
    void cancelOrderShouldThrowNoSuchElementExceptionWhenOutboxIsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId, "Test reason"))
                .isInstanceOf(NoSuchElementException.class);

        verify(inventoryGrpcClient, never()).releaseStock(anyString());
        verify(outboxMessageRepository, never()).save(any(OutboxMessage.class));
    }

    @Test
    @DisplayName("Получение заказа по ID: успешный возврат OrderResponse")
    void getOrderByIdShouldReturnOrderResponseWhenOrderExists() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.RESERVED)
                .totalAmount(new BigDecimal("200.00"))
                .build();

        OrderItem item = OrderItem.builder()
                .stockId("SKU-100")
                .id(orderItemId)
                .quantity(2)
                .price(new BigDecimal("200.00"))
                .build();

        order.addItem(item);

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, userId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("Получение заказа по ID: выброс NoSuchElementException если заказ не найден")
    void getOrderByIdShouldThrowNoSuchElementExceptionWhenOrderNotFound() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId, userId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Получение списка заказа пользователя: возврат страницы с заказами")
    void getUserOrdersShouldReturnPagedOrderResponses() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Order firstOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVED)
                .totalAmount(new BigDecimal("200.00"))
                .build();

        Order secondtOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        Page<Order> orderPage = new PageImpl<>(List.of(firstOrder, secondtOrder), pageable, 2);

        when(orderRepository.findAllByUserId(userId, pageable)).thenReturn(orderPage);

        Page<OrderResponse> result = orderService.getUserOrders(userId, pageable);

        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(result.getContent().get(1).status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
