package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID id,
        UUID userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String cancelReason,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
