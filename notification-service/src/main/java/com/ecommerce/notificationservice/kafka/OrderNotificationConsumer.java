package com.ecommerce.notificationservice.kafka;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";

    private final NotificationService notificationService;

    @KafkaListener(
            topics = ORDER_CREATED_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Kafka Consumer: received OrderCreatedEvent eventId={}, orderId={}",
                event.getEventId(), event.getOrderId());
        notificationService.processOrderCreated(event);
    }

    @KafkaListener(
            topics = ORDER_STATUS_CHANGED_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Kafka Consumer: received OrderStatusChangedEvent eventId={}, orderId={}, status={} -> {}",
                event.getEventId(), event.getOrderId(), event.getOldStatus(), event.getNewStatus());
        notificationService.processOrderStatusChanged(event);
    }
}
