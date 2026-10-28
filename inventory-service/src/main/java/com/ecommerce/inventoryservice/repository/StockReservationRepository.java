package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.ReservationStatus;
import com.ecommerce.inventoryservice.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findAllByOrderId(String orderId);
    List<StockReservation> findAllByOrderIdAndStatus(String orderId, ReservationStatus status);
}
