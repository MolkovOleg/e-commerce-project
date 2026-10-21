package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.dto.ReplenishStockRequest;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Controller", description = "Endpoints for mapping warehouse stock levels")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/replenish")
    @Operation(summary = "Replenish stock for a product")
    public ResponseEntity<InventoryResponse> replenishStock(
            @Valid @RequestBody ReplenishStockRequest request) {
        return ResponseEntity.ok(inventoryService.replenishStock(request));
    }

    @GetMapping("/{stockId}")
    @Operation(summary = "Get current stock details for product")
    public ResponseEntity<InventoryResponse> getStock(
            @PathVariable String stockId) {
        return ResponseEntity.ok(inventoryService.getStock(stockId));
    }
}
