package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByStockId(String stockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.stockId = :stockId")
    Optional<InventoryItem> findByStockIdWithLock(@Param("stockId") String stockId);
}
