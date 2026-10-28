package com.ecommerce.inventoryservice.dto;

import java.util.UUID;

public record InventoryResponse(
        UUID id,
        String stockId,
        int quantity,
        int reservedQuantity,
        int availableQuantity
) {
}
