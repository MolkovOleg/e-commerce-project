package com.ecommerce.notificationservice.service;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class NotificationServiceIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationLogRepository logRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        RedisConnectionFactory connection = redisTemplate.getConnectionFactory();
        if (connection != null) {
            connection.getConnection().serverCommands().flushDb();
        }
    }

    @Test
    @DisplayName("Обработка OrderCreatedEvent: отправка по каналам EMAIL и SMS, аудит SENT в MongoDB")
    void processedOrderCreatedShouldSendNotificationsAndAuditToMongo() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String userId = "user-order-1";

        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId(eventId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setTotalAmount("4500.00")
                .setStatus("RESERVED")
                .setItems(Collections.emptyList())
                .setCreatedAt(Instant.now())
                .build();

        notificationService.processOrderCreated(event);

        // Проверяем сохранение записей аудита в MongoDB
        List<NotificationLog> logs = logRepository.findAll();
        assertThat(logs).hasSize(2);

        NotificationLog emailLog = logs.stream()
                .filter(l -> l.getChannel() == NotificationChannel.EMAIL)
                .findFirst()
                .orElseThrow();
        assertThat(emailLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(emailLog.getSubject()).contains(orderId);
        assertThat(emailLog.getBody()).contains("4500.00");

        NotificationLog smsLog = logs.stream()
                .filter(l -> l.getChannel() == NotificationChannel.SMS)
                .findFirst()
                .orElseThrow();
        assertThat(smsLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(smsLog.getBody()).contains(orderId);
    }

    @Test
    @DisplayName("Идемпотентность: дублирующее событие игнорируется и не сохраняет дубликаты в Mongo")
    void processOrderCreatedShouldBeIdempotentOnDuplicateEvents() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String userId = "user-idempotent";

        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId(eventId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setTotalAmount("1200.00")
                .setStatus("RESERVED")
                .setItems(Collections.emptyList())
                .setCreatedAt(Instant.now())
                .build();

        // Первый вызов
        notificationService.processOrderCreated(event);
        assertThat(logRepository.findAll()).hasSize(2);

        // Повторный вызов того же самого события
        notificationService.processOrderCreated(event);
        // Количество записей не должно увеличиться
        assertThat(logRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Rate Limiter: превышение емкости корзины Leaky Bucket в Redis фиксирует RATE_LIMITED в Mongo")
    void leakyBucketRateLimiterShouldRejectExceededRequests() {
        String userId = "spammer-user";

        for (int i = 1; i <= 6; i++) {
            OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setOrderId(UUID.randomUUID().toString())
                    .setUserId(userId)
                    .setOldStatus("NEW")
                    .setNewStatus("CANCELLED")
                    .setReason("Status update #" + i)
                    .setUpdatedAt(Instant.now())
                    .build();

            notificationService.processOrderStatusChanged(event);
        }

        // Проверяем, что среди записей аудита ест заблокированные лимитером
        List<NotificationLog> logs = logRepository.findAll();
        assertThat(logs).isNotEmpty();

        long rateLimitedCount = logs.stream()
                .filter(l -> l.getStatus() == NotificationStatus.RATE_LIMITED)
                .count();
        assertThat(rateLimitedCount).isPositive();

        // Проверяем получение истории через сервисный метод с пагинацией
        Page<NotificationResponse> page = notificationService.getUserNotifications(
                userId, PageRequest.of(0, 20)
        );
        assertThat(page.getTotalElements()).isEqualTo(logs.size());
    }
}
