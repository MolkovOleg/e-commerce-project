package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Успешное создание продукта")
    void testCreateProduct_Successful_ReturnsProductResponse() {
        ProductCreateRequest request = new ProductCreateRequest(
                "MacBook Pro 16",
                "Apple Silicon M3 Max",
                "Laptops",
                BigDecimal.valueOf(3500.0),
                Map.of("ram", "32GB"),
                List.of("https://img.com/1.jpg")
        );

        Product savedProduct = Product.builder()
                .id("prod-1")
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .price(request.price())
                .status(ProductStatus.DRAFT)
                .attributes(request.attributes())
                .images(request.images())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("prod-1");
        assertThat(response.name()).isEqualTo("MacBook Pro 16");
        assertThat(response.status()).isEqualTo(ProductStatus.DRAFT);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Поиск продукта по ID: продукт существует")
    void testGetProductById_Exists_ReturnsProductResponse() {
        Product product = Product.builder()
                .id("prod-123")
                .name("iPhone 15")
                .category("Smartphones")
                .price(BigDecimal.valueOf(999.00))
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById("prod-123");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("prod-123");
        assertThat(response.name()).isEqualTo("iPhone 15");
        assertThat(response.status()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(response.description()).isNull();
    }

    @Test
    @DisplayName("Поиск продукта по несуществующему ID: NoSuchElementException")
    void testGetProductById_NotFound_ReturnsNoSuchElementException() {
        when(productRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById("unknown-id"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Product with id=unknown-id not found");

    }

    @Test
    @DisplayName("Изменение карточки продукта")
    void testUpdateProduct_Successful_ReturnsProductResponse() {
        Product product = Product.builder()
                .id("prod-123")
                .name("iPhone 15")
                .category("Smartphones")
                .price(BigDecimal.valueOf(999.00))
                .attributes(Map.of("ram", "8GB", "memory", "512GB"))
                .status(ProductStatus.DRAFT)
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById("prod-123");
        assertThat(response.name()).isEqualTo("iPhone 15");
        assertThat(response.images()).isNull();

        ProductUpdateRequest request = new ProductUpdateRequest(
                "iPhone 16",
                null,
                "Smartphones",
                BigDecimal.valueOf(999.00),
                null,
                List.of("check update")
        );

        product.setName(request.name());
        product.setImages(request.images());

        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse updatedResponse = productService.updateProduct("prod-123", request);

        assertThat(updatedResponse.name()).isEqualTo("iPhone 16");
        assertThat(updatedResponse.images()).hasSize(1);

    }

    @Test
    @DisplayName("Изменение статуса продукта")
    void testChangeStatus_Successful_ReturnsProductResponse() {
        Product product = Product.builder()
                .id("prod-123")
                .name("iPhone 15")
                .category("Smartphones")
                .price(BigDecimal.valueOf(999.00))
                .attributes(Map.of("ram", "8GB", "memory", "512GB"))
                .status(ProductStatus.DRAFT)
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.changeStatus("prod-123", ProductStatus.PUBLISHED);

        assertThat(response.status()).isEqualTo(ProductStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Удаление товара по ID")
    void testDeleteProductById_Successful() {
        Product product = Product.builder()
                .id("prod-123")
                .name("iPhone 15")
                .category("Smartphones")
                .price(BigDecimal.valueOf(999.00))
                .attributes(Map.of("ram", "8GB", "memory", "512GB"))
                .status(ProductStatus.DRAFT)
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.deleteProduct("prod-123");

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Получение каталога товаров с фильтрацией по категории и цене")
    void testGetPublicCatalog_WithFilters_ReturnsFilteredProducts() {
        Product product = Product.builder()
                .id("prod-1")
                .name("iPhone 15")
                .category("Smartphones")
                .price(BigDecimal.valueOf(999.00))
                .status(ProductStatus.PUBLISHED)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findByStatusAndCategoryAndPriceBetween(
                eq(ProductStatus.PUBLISHED),
                eq("Smartphones"),
                eq(BigDecimal.valueOf(500.0)),
                eq(BigDecimal.valueOf(1500.0)),
                eq(pageable)
        )).thenReturn(page);

        Page<ProductResponse> result = productService.getPublicCatalog(
                "Smartphones",
                BigDecimal.valueOf(500.0),
                BigDecimal.valueOf(1500.0),
                pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("iPhone 15");
    }

    @Test
    @DisplayName("Получение каталога товаров без фильтрации")
    void testGetPublicCatalog_WithoutFilters_ReturnsAllPublishedProducts() {
        Product product = Product.builder()
                .id("prod-2")
                .name("MacBook Pro 16")
                .category("Laptops")
                .price(BigDecimal.valueOf(2500.00))
                .status(ProductStatus.PUBLISHED)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findByStatus(ProductStatus.PUBLISHED, pageable))
                .thenReturn(page);

        Page<ProductResponse> result = productService.getPublicCatalog(null, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("MacBook Pro 16");
    }

    @Test
    @DisplayName("Полнотекстовый поиск товаров по слову или тексту")
    void testSearchProductsByText_Successful() {
        Product product = Product.builder()
                .id("prod-3")
                .name("iPhone 15 Pro Max")
                .category("Smartphones")
                .price(BigDecimal.valueOf(1199.00))
                .status(ProductStatus.PUBLISHED)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findAllBy(any(TextCriteria.class), eq(pageable)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.searchProductsByText("iPhone", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("iPhone 15 Pro Max");

        verify(productRepository).findAllBy(any(TextCriteria.class), eq(pageable));
    }
}
