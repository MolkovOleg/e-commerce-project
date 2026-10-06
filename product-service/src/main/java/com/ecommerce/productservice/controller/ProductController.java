package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product catalog controller", description = "Endpoints for catalog browsing, search and product management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get published products catalog with pagination and optional category/price filter")
    public ResponseEntity<Page<ProductResponse>> getCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(productService.getPublicCatalog(category, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID (cached via Redis Cache-Aside)")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String text,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.searchProductsByText(text, pageable));
    }

    @PostMapping
    @Operation(summary = "Create a new product draft (Managers / Admins)")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.
                status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductUpdateRequest request) {

        return ResponseEntity.
                ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> changeStatus(
            @PathVariable String id,
            @RequestParam ProductStatus status) {
        return ResponseEntity.ok(productService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
