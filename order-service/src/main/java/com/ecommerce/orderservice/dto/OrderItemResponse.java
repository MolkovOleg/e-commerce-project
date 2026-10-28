package com.ecommerce.orderservice.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderItemResponse(
        UUID id,
        String stockId,
        Integer quantity,
        BigDecimal price
) {
}
