package com.ecommerce.notificationservice.service;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.ratelimit.LeakyBucketRateLimiter;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository logRepository;
    private final NotificationTemplateRepository templateRepository;
    private final LeakyBucketRateLimiter rateLimiter;

    /**
     * Обработка события создания заказа (order.created)
     */
    public void processOrderCreated(OrderCreatedEvent event) {
        log.info("Processing OrderCreatedEvent: eventId={}, orderId={}, userId={}",
                event.getEventId(), event.getOrderId(), event.getUserId());

        Map<String, String> variables = new HashMap<>();
        variables.put("orderId", event.getOrderId());
        variables.put("userId", event.getUserId());
        variables.put("totalAmount", event.getTotalAmount());
        variables.put("status", event.getStatus());

        processNotification(
                event.getEventId(),
                event.getUserId(),
                event.getOrderId(),
                NotificationType.ORDER_CREATED,
                variables
        );
    }

    /**
     * Обработка события изменения статуса заказа (order.status-changed)
     */
    public void processOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Processing OrderStatusChangedEvent: eventId={}, orderId={}, status={} -> {}",
                event.getEventId(), event.getOrderId(), event.getOldStatus(), event.getNewStatus());

        Map<String, String> variables = new HashMap<>();
        variables.put("orderId", event.getOrderId());
        variables.put("userId", event.getUserId());
        variables.put("oldStatus", event.getOldStatus());
        variables.put("newStatus", event.getNewStatus());
        variables.put("reason", event.getReason() != null ? event.getReason() : "Not specified");

        processNotification(
                event.getEventId(),
                event.getUserId(),
                event.getOrderId(),
                NotificationType.ORDER_STATUS_CHANGED,
                variables
        );
    }

    /**
     * Получение истории уведоилений пользователя с пагинацией
     */
    public Page<NotificationResponse> getUserNotifications(String userId, Pageable pageable) {
        return logRepository.findAllByUserId(userId, pageable)
                .map(this::mapToNotificationResponse);
    }

    private void processNotification(
            String eventId,
            String userId,
            String orderId,
            NotificationType type,
            Map<String, String> variables) {

        List<NotificationTemplate> templates = templateRepository.findAllByTypeAndActiveTrue(type);

        if (templates.isEmpty()) {
            log.warn("No active templates found for tyep={}", type);
            return;
        }

        for (NotificationTemplate template : templates) {
            NotificationChannel channel = template.getChannel();

            // 1. Проверка идемпотентности
            if (logRepository.existsByEventIdAndChannel(eventId, channel)) {
                log.info("Duplicate even detected, skipping: eventId={}, channel={}", eventId, channel);
                continue;
            }

            // 2. Проверка Rate Limiter (защита от спама)
            boolean allowed = rateLimiter.tryAcquire(userId);
            if (!allowed) {
                log.warn("Notification dropped due to rate limit: userId={}, type={}, channel={}",
                        userId, type, channel);

                NotificationLog rateLimitedLog = NotificationLog.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .orderId(orderId)
                        .type(type)
                        .channel(channel)
                        .status(NotificationStatus.RATE_LIMITED)
                        .errorMessage("Rate limit exceeded")
                        .createdAt(Instant.now())
                        .build();

                logRepository.save(rateLimitedLog);
                continue;
            }

            // 3. Формирования шаблона если никаких ошибок
            String subject = renderTemplate(template.getSubjectTemplate(), variables);
            String body = renderTemplate(template.getBodyTemplate(), variables);

            // 4. Имитация отправки (Email/SMS)
            sendNotification(userId, channel, subject, body);

            // 5. Сохранение аудита в MongoDB
            NotificationLog sentLog = NotificationLog.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .orderId(orderId)
                    .type(type)
                    .channel(channel)
                    .subject(subject)
                    .body(body)
                    .status(NotificationStatus.SENT)
                    .createdAt(Instant.now())
                    .build();

            logRepository.save(sentLog);
            log.info("Notification successfully sent and audited: eventId={}, userId={}, channel={}",
                    eventId, userId, channel);
        }
    }

    private void sendNotification(String userId, NotificationChannel channel, String subject, String body) {
        switch (channel) {
            case EMAIL -> log.info("[EMAIL] To userId={}, Subject: '{}', Body: '{}'", userId, subject, body);
            case SMS -> log.info("[SMS] To userId={}, Message: '{}'", userId, body);
            default -> log.warn("Unsupported channel: {}", channel);
        }
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    private NotificationResponse mapToNotificationResponse(NotificationLog notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .eventId(notification.getEventId())
                .userId(notification.getUserId())
                .orderId(notification.getOrderId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .errorMessage(notification.getErrorMessage())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
