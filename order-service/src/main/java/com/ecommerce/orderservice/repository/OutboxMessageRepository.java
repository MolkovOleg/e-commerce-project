package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.OutboxMessage;
import com.ecommerce.orderservice.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status,  Pageable pageable);
}
