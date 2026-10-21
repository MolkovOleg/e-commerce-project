package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.inventoryservice.domain.InventoryItem;
import com.ecommerce.inventoryservice.domain.ReservationStatus;
import com.ecommerce.inventoryservice.domain.StockReservation;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.dto.ReplenishStockRequest;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository stockReservationRepository;

    /**
     * Пополнение остатков товара на складе или создание нового (REST API)
     */
    @Transactional
    public InventoryResponse replenishStock(ReplenishStockRequest request) {
        InventoryItem item = inventoryRepository.findByStockId(request.stockId())
                .map(existingItem -> {
                    existingItem.setQuantity(existingItem.getQuantity() + request.quantity());
                    return existingItem;
                })
                .orElseGet(() -> InventoryItem.builder()
                        .stockId(request.stockId())
                        .quantity(request.quantity())
                        .reservedQuantity(0)
                        .build());

        InventoryItem savedItem = inventoryRepository.save(item);
        log.info("Success replenish items with stockId={} quantity={} and total quantity={}",
                request.stockId(), request.quantity(), savedItem.getQuantity());
        return mapToResponse(savedItem);
    }

    /**
     * Получение информации об остатке (REST & gRPC CheckStock)
     */
    @Transactional(readOnly = true)
    public InventoryResponse getStock(String stockId) {
        InventoryItem item = inventoryRepository.findByStockId(stockId)
                .orElseThrow(() -> new NoSuchElementException("Stock item not found for stockId=" + stockId));
        return mapToResponse(item);
    }

    /**
     * Атомарное резервирование товаров под заказ (gRPC ReserveStock)
     * Использует пессимистичную блокировку для защиты от Race Condition
     */
    @Transactional
    public boolean reserveStock(String orderId, List<StockItem> items) {
        log.info("Trying to reserve items with orderId={}, itemsCount={}", orderId,  items.size());

        for (StockItem item : items) {
            InventoryItem ii = inventoryRepository.findByStockIdWithLock(item.getStockId())
                    .orElse(null);
            if (ii == null || !ii.isAvailable(item.getQuantity())) {
                log.warn("No such item with stockId={} or not enough items for reservation, only quantity={}",
                        item.getStockId(), item.getQuantity());
                return false;
            }
        }

        for (StockItem item : items) {
            InventoryItem ii = inventoryRepository.findByStockIdWithLock(item.getStockId())
                    .orElseThrow();

            ii.reserve(item.getQuantity());
            inventoryRepository.save(ii);

            StockReservation reservation = StockReservation.builder()
                    .orderId(orderId)
                    .stockId(item.getStockId())
                    .quantity(item.getQuantity())
                    .status(ReservationStatus.RESERVED)
                    .build();

            stockReservationRepository.save(reservation);
        }

        log.info("Success reservation of items with orderId={}", orderId);
        return true;
    }

    /**
     * Компенсирующее действие SAGA: снятие резерва при сбое заказа (gRPC ReleaseStock)
     */
    @Transactional
    public void releaseStock(String orderId) {
        log.info("Release items if some problems with orderId={}", orderId);

        List<StockReservation> reservations = stockReservationRepository.findAllByOrderIdAndStatus(
                orderId, ReservationStatus.RESERVED);

        for (StockReservation reservation : reservations) {
            inventoryRepository.findByStockIdWithLock(reservation.getStockId())
                    .ifPresent(item -> {
                        item.release(reservation.getQuantity());
                        inventoryRepository.save(item);
                    });
            reservation.setStatus(ReservationStatus.RELEASED);
            stockReservationRepository.save(reservation);
        }

        log.info("Release {} items with orderId={}", reservations.size(), orderId);
    }

    private InventoryResponse mapToResponse(InventoryItem item) {
        return new InventoryResponse(
                item.getId(),
                item.getStockId(),
                item.getQuantity(),
                item.getReservedQuantity(),
                item.getAvailableQuantity()
        );
    }
}
