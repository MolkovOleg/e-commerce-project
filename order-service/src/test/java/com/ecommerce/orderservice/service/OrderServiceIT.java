package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.domain.OutboxMessage;
import com.ecommerce.orderservice.domain.OutboxStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.grpc.InventoryGrpcClient;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.repository.OutboxMessageRepository;
import com.ecommerce.orderservice.scheduler.OutboxScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "outbox.scheduler.fixed-delay=9999999")
@Testcontainers
class OrderServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @MockBean
    private InventoryGrpcClient inventoryGrpcClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outboxMessageRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Создание заказа: успешный резерв gRPC -> заказ в статусе RESERVED и событие в Outbox")
    void createOrderShouldPersistOrderAndOutboxEventWhenStockReserved() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("SKU-100", 2, new BigDecimal("1500.00")),
                new OrderItemRequest("SKU-200", 1, new BigDecimal("2000.00"))
        ));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(true);

        OrderResponse response = orderService.createOrder(userId, request);

        // 1. Проверяем сущность заказа в реальной БД PostgreSQL
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(response.totalAmount()).isEqualByComparingTo("5000.00");

        Order savedOrder = orderRepository.findById(response.id()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("5000.00");

        OrderResponse fetchOrder = orderService.getOrderById(response.id(), userId);
        assertThat(fetchOrder.items()).hasSize(2);

        // 2. Проверяем сохранение Transactional Outbox события
        List<OutboxMessage> outboxMessages = outboxMessageRepository.findAll();
        assertThat(outboxMessages).hasSize(1);

        OutboxMessage outboxMessage = outboxMessages.getFirst();
        assertThat(outboxMessage.getTopic()).isEqualTo(OrderService.ORDER_CREATED_TOPIC);
        assertThat(outboxMessage.getAggregateId()).isEqualTo(response.id().toString());
        assertThat(outboxMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxMessage.getPayload()).contains(response.id().toString());
    }

    @Test
    @DisplayName("Создание заказа: отказ склада по gRPC -> заказ CANCELLED, в Outbox нет событий")
    void createOrderShouldCancelOrderWithoutOutboxWhenStockUnavailable() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("SKU-100", 2, new BigDecimal("1500.00"))
        ));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(false);

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Insufficient stock or unavailable");

        Order savedOrder = orderRepository.findById(response.id()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Событий в Outbox БД никаких не должно было отправиться
        List<OutboxMessage> outboxMessages = outboxMessageRepository.findAll();
        assertThat(outboxMessages).isEmpty();
    }

    @Test
    @DisplayName("SAGA отмена: отмена заказа -> вызов releaseStock gRPC и Outbox событие status-changed")
    void cancelOrderShouldCompensateStockAndSaveStatusChangedOutbox() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("SKU-SAGA-ORDER", 1, new BigDecimal("3500.00"))
        ));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(true);
        OrderResponse createResponse = orderService.createOrder(userId, request);

        // Очистка Outbox после создания, чтобы изолировано проверить событие отмены
        outboxMessageRepository.deleteAll();

        OrderResponse cancelResponse = orderService.cancelOrder(createResponse.id(), userId, "Test reason");

        // 1. Проверяем компенсацию склада
        verify(inventoryGrpcClient).releaseStock(createResponse.id().toString());
        assertThat(cancelResponse.status()).isEqualTo(OrderStatus.CANCELLED);

        // 2. Проверяем статус в БД
        Order orderInDB = orderRepository.findById(createResponse.id()).orElseThrow();
        assertThat(orderInDB.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderInDB.getCancelReason()).isEqualTo("Test reason");

        // 3. Проверяем Outbox событие изменения заказа
        List<OutboxMessage> outboxMessages = outboxMessageRepository.findAll();
        assertThat(outboxMessages).hasSize(1);

        OutboxMessage outboxMessage = outboxMessages.getFirst();
        assertThat(outboxMessage.getTopic()).isEqualTo(OrderService.ORDER_STATUS_CHANGED_TOPIC);
        assertThat(outboxMessage.getAggregateId()).isEqualTo(createResponse.id().toString());
        assertThat(outboxMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxMessage.getPayload()).contains("CANCELLED");
    }

    @Test
    @DisplayName("Outbox Scheduler: обработка PENDING сообщений и перевод статуса в SENT")
    void outboxSchedulerShouldPublishPendingMessageAndMarkAsSent() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("SKU-PUBLISH-100", 1, new BigDecimal("1500.00"))
        ));

        when(inventoryGrpcClient.reserveStock(anyString(), anyList())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        orderService.createOrder(userId, request);

        // Проверка состояния статуса заказа до процесса считывания слушателем
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findAll();
        assertThat(pendingMessages).hasSize(1);
        assertThat(pendingMessages.getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);

        // Запуск слушателя
        outboxScheduler.processOutboxMessages();

        // Проверка вызова KafkaTemplate
        verify(kafkaTemplate).send(
                eq(OrderService.ORDER_CREATED_TOPIC),
                eq(pendingMessages.getFirst().getAggregateId()),
                any()
        );

        // Проверяем обновление статуса в БД
        OutboxMessage processedMessage =
                outboxMessageRepository.findById(
                        pendingMessages.getFirst().getId()).orElseThrow();
        assertThat(processedMessage.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(processedMessage.getProcessedAt()).isNotNull();
    }
}
