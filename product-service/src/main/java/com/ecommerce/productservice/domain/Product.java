package com.ecommerce.productservice.domain;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
@CompoundIndexes({
        @CompoundIndex(name = "category_status_idx", def = "{'category': 1, 'status': 1}"),
        @CompoundIndex(name = "price_status_idx", def = "{'price': 1, 'status': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    @TextIndexed(weight = 3) // Приоритет при полнотекстовом поиске
    private String name;

    @TextIndexed(weight = 1)
    private String description;

    private String category;

    private BigDecimal price;

    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    private Map<String, Object> attributes;

    private List<String> images;

    private Instant createdAt;

    private Instant updatedAt;

}
