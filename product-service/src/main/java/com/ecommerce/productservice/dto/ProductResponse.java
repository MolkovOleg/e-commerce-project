package com.ecommerce.productservice.dto;

import com.ecommerce.productservice.domain.ProductStatus;
import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
public record ProductResponse(
        String id,
        String name,
        String description,
        String category,
        BigDecimal price,
        ProductStatus status,
        Map<String, Object> attributes,
        List<String> images,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
