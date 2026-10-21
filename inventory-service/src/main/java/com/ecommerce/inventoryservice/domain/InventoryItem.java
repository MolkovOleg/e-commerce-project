package com.ecommerce.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_id",  nullable = false, unique = true, length = 100)
    private String stockId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @CreationTimestamp
    @Column(name = "created_at",  nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at",   nullable = false)
    private Instant updatedAt;

    /**
     **************************************
     * Бизнес методы инкапсуляции состояния
     **************************************
     */

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public boolean isAvailable(int requiredQuantity) {
        return getAvailableQuantity() >= requiredQuantity;
    }

    public void reserve(int amount) {
        if (!isAvailable(amount)) {
            throw new IllegalStateException("Not enough stock for stockId=" + stockId);
        }
        this.reservedQuantity += amount;
    }

    public void release(int amount) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - amount);
    }
}
