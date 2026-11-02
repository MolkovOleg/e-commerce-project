package com.ecommerce.notificationservice.dto;

import com.ecommerce.notificationservice.domain.NotificationChannel;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import lombok.Builder;

import java.time.Instant;

@Builder
public record NotificationResponse(
        String id,
        String eventId,
        String userId,
        String orderId,
        NotificationType type,
        NotificationChannel channel,
        String subject,
        String body,
        NotificationStatus status,
        String errorMessage,
        Instant createdAt
) {
}
