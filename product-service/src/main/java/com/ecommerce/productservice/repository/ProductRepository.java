package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.math.BigDecimal;

public interface ProductRepository extends MongoRepository<Product, String> {

    // Каталог опубликованных товаров
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    // Каталог опубликованных товаров по категории и цене
    Page<Product> findByStatusAndCategoryAndPriceBetween(
            ProductStatus status,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    // Полнотекстовый поиск
    Page<Product> findAllBy(TextCriteria criteria,  Pageable pageable);
}
