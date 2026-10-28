package com.ecommerce.orderservice.dto.outbox;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record OrderStatusChangedPayload(
        UUID eventId,
        UUID orderId,
        UUID userId,
        String oldStatus,
        String newStatus,
        String reason,
        Instant updatedAt
) {
}
