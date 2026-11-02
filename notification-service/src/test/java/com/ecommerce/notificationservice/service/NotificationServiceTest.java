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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private LeakyBucketRateLimiter rateLimiter;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("Создание заказа: отправка EMAIL и SMS с подстановкой шаблонов и записью SENT в MongoDB")
    void processedOrderCreatedShouldSendEmailAndSmsAndSaveLogsWhenAllowed() {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId("event-100")
                .setOrderId("order-100")
                .setUserId("user-100")
                .setTotalAmount("1500.00")
                .setItems(Collections.emptyList())
                .setStatus("RESERVED")
                .setCreatedAt(Instant.now())
                .build();

        NotificationTemplate emailTemplate = NotificationTemplate.builder()
                .type(NotificationType.ORDER_CREATED)
                .channel(NotificationChannel.EMAIL)
                .subjectTemplate("Order #{{orderId}}")
                .bodyTemplate("Total: {{totalAmount}}, status: {{status}}")
                .build();

        NotificationTemplate smsTemplate = NotificationTemplate.builder()
                .type(NotificationType.ORDER_CREATED)
                .channel(NotificationChannel.SMS)
                .subjectTemplate(null)
                .bodyTemplate("SMS order #{{orderId}}")
                .build();

        when(templateRepository.findAllByTypeAndActiveTrue(NotificationType.ORDER_CREATED))
                .thenReturn(List.of(emailTemplate, smsTemplate));
        when(logRepository.existsByEventIdAndChannel(anyString(), any(NotificationChannel.class)))
                .thenReturn(false);
        when(rateLimiter.tryAcquire("user-100")).thenReturn(true);

        notificationService.processOrderCreated(event);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(logCaptor.capture());

        List<NotificationLog> savedLogs = logCaptor.getAllValues();
        assertThat(savedLogs).hasSize(2);

        NotificationLog emailLog = savedLogs.get(0);
        assertThat(emailLog.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(emailLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(emailLog.getSubject()).isEqualTo("Order #order-100");
        assertThat(emailLog.getBody()).isEqualTo("Total: 1500.00, status: RESERVED");

        NotificationLog smsLog = savedLogs.get(1);
        assertThat(smsLog.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(smsLog.getSubject()).isNull();
        assertThat(smsLog.getBody()).isEqualTo("SMS order #order-100");

    }

    @Test
    @DisplayName("Идемпотентность: пропуск дубликата события без вызова Rate Limiter и повтороной записи")
    void processOrderCreatedShouldSkipSendingWhenDuplicateEventDetected() {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId("event-dup")
                .setOrderId("order-200")
                .setUserId("user-300")
                .setTotalAmount("500.00")
                .setItems(Collections.emptyList())
                .setStatus("RESERVED")
                .setCreatedAt(Instant.now())
                .build();

        NotificationTemplate emailTemplate = NotificationTemplate.builder()
                .type(NotificationType.ORDER_CREATED)
                .channel(NotificationChannel.EMAIL)
                .subjectTemplate("Order #{{orderId}}")
                .bodyTemplate("Body")
                .build();

        when(templateRepository.findAllByTypeAndActiveTrue(NotificationType.ORDER_CREATED))
                .thenReturn(List.of(emailTemplate));
        when(logRepository.existsByEventIdAndChannel("event-dup", NotificationChannel.EMAIL))
                .thenReturn(true);

        notificationService.processOrderCreated(event);

        verify(rateLimiter, never()).tryAcquire(anyString());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Rate Limiter: сохранение статуса RATE_LIMITER при превышении лимита уведомлений")
    void processOrderCreatedShouldSaveRateLimitedLogWhenRateLimitExceeded() {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId("event-spam")
                .setOrderId("order-200")
                .setUserId("user-300")
                .setTotalAmount("500.00")
                .setItems(Collections.emptyList())
                .setStatus("RESERVED")
                .setCreatedAt(Instant.now())
                .build();

        NotificationTemplate emailTemplate = NotificationTemplate.builder()
                .type(NotificationType.ORDER_CREATED)
                .channel(NotificationChannel.EMAIL)
                .subjectTemplate("Order #{{orderId}}")
                .bodyTemplate("Body")
                .build();

        when(templateRepository.findAllByTypeAndActiveTrue(NotificationType.ORDER_CREATED))
                .thenReturn(List.of(emailTemplate));
        when(logRepository.existsByEventIdAndChannel(anyString(), any(NotificationChannel.class)))
                .thenReturn(false);
        when(rateLimiter.tryAcquire("user-300")).thenReturn(false);

        notificationService.processOrderCreated(event);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(savedLog.getStatus()).isEqualTo(NotificationStatus.RATE_LIMITED);
        assertThat(savedLog.getErrorMessage()).isEqualTo("Rate limit exceeded");
        assertThat(savedLog.getSubject()).isNull();
        assertThat(savedLog.getBody()).isNull();
    }

    @Test
    @DisplayName("Смена статуса заказа: подстановка дефолтной причины когда равен null")
    void processOrderStatusChangedShouldRenderDefaultReasonWhenReasonIsNul() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setEventId("event-status-1")
                .setOrderId("order-500")
                .setUserId("user-300")
                .setOldStatus("RESERVED")
                .setNewStatus("CANCELLED")
                .setReason(null)
                .setUpdatedAt(Instant.now())
                .build();

        NotificationTemplate emailTemplate = NotificationTemplate.builder()
                .type(NotificationType.ORDER_STATUS_CHANGED)
                .channel(NotificationChannel.EMAIL)
                .subjectTemplate("Status: {{newStatus}}")
                .bodyTemplate("Changed {{oldStatus}} -> {{newStatus}}. Reason: {{reason}}")
                .build();

        when(templateRepository.findAllByTypeAndActiveTrue(NotificationType.ORDER_STATUS_CHANGED))
                .thenReturn(List.of(emailTemplate));
        when(logRepository.existsByEventIdAndChannel("event-status-1", NotificationChannel.EMAIL))
                .thenReturn(false);
        when(rateLimiter.tryAcquire("user-300")).thenReturn(true);

        notificationService.processOrderStatusChanged(event);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(savedLog.getBody()).isEqualTo("Changed RESERVED -> CANCELLED. Reason: Not specified");
    }

    @Test
    @DisplayName("История уведомлений: возврат страницы уведомлений пользователя")
    void getUserNotificationsShouldReturnPagedResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        NotificationLog logEntry = NotificationLog.builder()
                .id("log-1")
                .eventId("event-1")
                .userId("user-300")
                .orderId("order-200")
                .type(NotificationType.ORDER_CREATED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .build();

        Page<NotificationLog> logPage = new PageImpl<>(List.of(logEntry),  pageable, 1);
        when(logRepository.findAllByUserId("user-300", pageable)).thenReturn(logPage);

        Page<NotificationResponse> result = notificationService.getUserNotifications("user-300", pageable);

        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("log-1");
        assertThat(result.getContent().get(0).status()).isEqualTo(NotificationStatus.SENT);
    }
}


