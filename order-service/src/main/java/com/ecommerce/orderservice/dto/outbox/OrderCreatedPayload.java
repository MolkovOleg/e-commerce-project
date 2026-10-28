package com.ecommerce.orderservice.dto.outbox;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreatedPayload(
        UUID eventId,
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        String status,
        List<OrderItemPayload> items,
        Instant createdAt
) {
    @Builder
    public record OrderItemPayload(
            String stockId,
            Integer quantity,
            BigDecimal price
    ){
    }
}
