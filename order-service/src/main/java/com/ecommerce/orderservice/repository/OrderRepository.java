package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
