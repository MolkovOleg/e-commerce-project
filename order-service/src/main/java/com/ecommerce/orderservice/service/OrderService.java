package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.domain.OutboxMessage;
import com.ecommerce.orderservice.domain.OutboxStatus;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemResponse;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.outbox.OrderCreatedPayload;
import com.ecommerce.orderservice.dto.outbox.OrderStatusChangedPayload;
import com.ecommerce.orderservice.grpc.InventoryGrpcClient;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";
    public static final String AGGREGATE_TYPE_ORDER = "ORDER";

    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final ObjectMapper objectMapper;

    /**
     * Создание заказа: SAGA + Transactional Outbox
     */
    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        log.info("Starting order creation for userId={}, itemsCount={}", userId, request.items().size());

        // 1. Создание сущности заказа и его товаров
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.NEW)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (var itemReq : request.items()) {
            OrderItem item = OrderItem.builder()
                    .stockId(itemReq.stockId())
                    .quantity(itemReq.quantity())
                    .price(itemReq.price())
                    .build();

            order.addItem(item);

            BigDecimal itemTotal = itemReq.price().multiply(BigDecimal.valueOf(itemReq.quantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);

        // 2. SAGA действие: Синхронный вызов inventory-service по gRPC
        boolean stockReserved = inventoryGrpcClient.reserveStock(orderId.toString(), order.getItems());

        if (!stockReserved) {
            log.warn("SAGA: Inventory reservation failed for orderId={}. Cancelling order...",  orderId);

            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Insufficient stock or unavailable");

            Order savedCancelledOrder = orderRepository.save(order);
            return mapToOrderResponse(savedCancelledOrder);
        }

        // SAGA действие: Синхроный вызов releasedStock по gRPC при ошибке сохранения заказа в базу данных
        try {
            order.setStatus(OrderStatus.RESERVED);
            Order reservedOrder = orderRepository.save(order);
            log.info("Order successfully created and reserved with orderId={}", reservedOrder.getId());

            savedOrderCreatedOutboxMessage(reservedOrder);

            return mapToOrderResponse(reservedOrder);
        } catch (Exception ex) {
           log.error("Failed to save order with orderId={} in DB after stock reservation. Use SAGA compensation", orderId, ex);
           inventoryGrpcClient.releaseStock(orderId.toString());
           throw ex;
        }
    }

    /**
     * SAGA компенсация: Отмена заказа и снятие брони
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId, String reason) {
        log.info("Attempting to cancel order with orderId={} by userId={}, reason={}", orderId, userId, reason);

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NoSuchElementException("Order not found with id=" + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            log.warn("Order {} cannot be cancelled from status {}", orderId, order.getStatus());
            return mapToOrderResponse(order);
        }

        OrderStatus oldStatus = order.getStatus();

        // 1. Если заказ держал бронь на складе - выполняем компенсирующее действие
        if (oldStatus == OrderStatus.RESERVED || oldStatus == OrderStatus.PAID) {
            inventoryGrpcClient.releaseStock(orderId.toString());
        }

        // 2. Обновляем статус заказа
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason != null ? reason : "Cancelled by user");
        order.setUpdatedAt(Instant.now());
        Order savedOrder = orderRepository.save(order);

        // 3. Сохраняем событие об изменение статуса в Outbox
        saveOrderStatusChangedOutboxMessage(savedOrder, oldStatus.name(), OrderStatus.CANCELLED.name(), reason);
        log.info("Order {} cancelled successfully", orderId);
        return mapToOrderResponse(savedOrder);
    }

    /**
     * Получение информации о заказе
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NoSuchElementException("Order not found with id=" + orderId));
        return mapToOrderResponse(order);
    }

    /**
     * Получение истории заказов пользователя
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable)
                .map(this::mapToOrderResponse);
    }

    private void saveOrderStatusChangedOutboxMessage(Order order, String oldStatus, String newStatus, String reason) {
        OrderStatusChangedPayload payload = OrderStatusChangedPayload.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .userId(order.getUserId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .updatedAt(Instant.now())
                .build();

        savedOutboxMessage(ORDER_STATUS_CHANGED_TOPIC, order.getId().toString(), payload);
    }

    private void savedOrderCreatedOutboxMessage(Order order) {
        List<OrderCreatedPayload.OrderItemPayload> items = order.getItems().stream()
                .map(item -> OrderCreatedPayload.OrderItemPayload.builder()
                        .stockId(item.getStockId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();

        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(items)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now())
                .build();

        savedOutboxMessage(ORDER_CREATED_TOPIC, order.getId().toString(), payload);
    }

    private void savedOutboxMessage(String topic, String aggregateId, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType(AGGREGATE_TYPE_ORDER)
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxMessageRepository.save(outboxMessage);
            log.debug("Outbox message saved for aggregateId={}, topic={}",  aggregateId, topic);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize outbox payload for aggregateId={}",  aggregateId, ex);
            throw new RuntimeException("Error serializing outbox event", ex);
        }
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .stockId(item.getStockId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .cancelReason(order.getCancelReason())
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
