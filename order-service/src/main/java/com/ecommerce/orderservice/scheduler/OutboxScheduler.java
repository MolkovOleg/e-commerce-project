package com.ecommerce.orderservice.scheduler;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderItemAvro;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.orderservice.domain.OutboxMessage;
import com.ecommerce.orderservice.domain.OutboxStatus;
import com.ecommerce.orderservice.dto.outbox.OrderCreatedPayload;
import com.ecommerce.orderservice.dto.outbox.OrderStatusChangedPayload;
import com.ecommerce.orderservice.repository.OutboxMessageRepository;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.scheduler.fixed-delay:2000}")
    @Transactional
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE));

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("Found {} pending messages to process", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                SpecificRecord avroRecord = buildAvroRecord(message);

                // Синхронно ждем подтверждение от брокера Kafka перед сменой статуса на SENT
                kafkaTemplate.send(message.getTopic(), message.getAggregateId(), avroRecord)
                        .get(5, TimeUnit.SECONDS);

                message.setStatus(OutboxStatus.SENT);
                message.setProcessedAt(Instant.now());
                message.setErrorMessage(null);

                log.info("Successfully published outbox message id={}, topic={} aggregateId={}",
                        message.getId(), message.getTopic(), message.getAggregateId());
            } catch (Exception ex) {
                int nextRetry = message.getRetryCount() + 1;
                message.setRetryCount(nextRetry);
                message.setErrorMessage(ex.getMessage());

                if (nextRetry >= MAX_RETRIES) {
                    message.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox message id={} exceeded max retries ({}) and marked as FAILED",
                            message.getId(), MAX_RETRIES, ex);
                } else {
                    log.warn("Failed to publish outbox message id={} (retry {}/{}): {}",
                            message.getId(), nextRetry, MAX_RETRIES, ex.getMessage());
                }
            }
            outboxMessageRepository.save(message);
        }
    }

    private SpecificRecord buildAvroRecord(OutboxMessage message) throws Exception {
        return switch (message.getTopic()) {
            case OrderService.ORDER_CREATED_TOPIC -> {
                OrderCreatedPayload payload = objectMapper.readValue(
                        message.getPayload(),
                        OrderCreatedPayload.class
                );

                List<OrderItemAvro> avroItems = payload.items().stream()
                        .map(item -> OrderItemAvro.newBuilder()
                                .setStockId(item.stockId())
                                .setQuantity(item.quantity())
                                .setPrice(item.price().toPlainString())
                                .build())
                        .toList();

                yield OrderCreatedEvent.newBuilder()
                        .setEventId(payload.eventId().toString())
                        .setOrderId(payload.orderId().toString())
                        .setUserId(payload.userId().toString())
                        .setTotalAmount(payload.totalAmount().toPlainString())
                        .setItems(avroItems)
                        .setStatus(payload.status())
                        .setCreatedAt(payload.createdAt())
                        .build();
            }
            case OrderService.ORDER_STATUS_CHANGED_TOPIC -> {
                OrderStatusChangedPayload payload = objectMapper.readValue(
                        message.getPayload(),
                        OrderStatusChangedPayload.class
                );

                yield OrderStatusChangedEvent.newBuilder()
                        .setEventId(payload.eventId().toString())
                        .setOrderId(payload.orderId().toString())
                        .setUserId(payload.userId().toString())
                        .setOldStatus(payload.oldStatus())
                        .setNewStatus(payload.newStatus())
                        .setReason(payload.reason())
                        .setUpdatedAt(payload.updatedAt())
                        .build();
            }
            default -> throw new IllegalArgumentException("Unsupported outbox topic " + message.getTopic());
        };
    }
}
