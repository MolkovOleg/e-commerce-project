package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.ProductCreateRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.dto.ProductUpdateRequest;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse createProduct(ProductCreateRequest request) {
        Product product = Product.builder()
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

        Product savedProduct = productRepository.save(product);
        log.info("Created product with id={}", savedProduct.getId());

        return mapToProductResponse(savedProduct);
    }

    /**
     * Получение карточки товара + кэширование (Cache-Aside)
     */
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(String id) {
        log.info("Fetching product from MongoDB with id={}", id);
        Product product = findProductByIdOrThrow(id);
        return mapToProductResponse(product);
    }

    /**
     * Изменение карточки продукта с удалением старого кэша
     */
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(String id, ProductUpdateRequest request) {
        Product product = findProductByIdOrThrow(id);

        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setAttributes(request.attributes());
        product.setImages(request.images());
        product.setUpdatedAt(Instant.now());

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product with id={} and evicted cache", updatedProduct.getId());
        return mapToProductResponse(updatedProduct);
    }

    /**
     * Смена статуса (публикация/архивация) с очисткой кэша
     */
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse changeStatus(String id, ProductStatus status) {
        Product product = findProductByIdOrThrow(id);
        product.setStatus(status);
        product.setUpdatedAt(Instant.now());

        Product updatedProduct = productRepository.save(product);
        log.info("Changed status for product with id={} and evicted cache", updatedProduct.getId());
        return mapToProductResponse(updatedProduct);
    }

    /**
     * Архивация товоара с очисткой кэша
     */
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(String id) {
        Product product = findProductByIdOrThrow(id);

        product.setStatus(ProductStatus.ARCHIVED);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        log.info("Archived product with id={} and evicted cache", product.getId());
    }

    /**
     *  Публичная витрина катологов с фильтрацией и пагинацией
     */
    public Page<ProductResponse> getPublicCatalog(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    ) {
        Page<Product> products;
        if (category != null && minPrice != null && maxPrice != null) {
            products = productRepository.findByStatusAndCategoryAndPriceBetween(
                    ProductStatus.PUBLISHED, category, minPrice, maxPrice, pageable
            );
        } else {
            products = productRepository.findByStatus(ProductStatus.PUBLISHED, pageable);
        }

        return products.map(this::mapToProductResponse);
    }

    /**
     * Полнотекстовый поиск по слову или тексту
     */
    public Page<ProductResponse> searchProductsByText(String text, Pageable pageable) {
        TextCriteria criteria = TextCriteria.forDefaultLanguage().matching(text);
        return productRepository.findAllBy(criteria, pageable)
                .map(this::mapToProductResponse);
    }

    private Product findProductByIdOrThrow(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product with id=" + id + " not found"));
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice())
                .status(product.getStatus())
                .attributes(product.getAttributes())
                .images(product.getImages())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
