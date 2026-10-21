package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.inventoryservice.domain.InventoryItem;
import com.ecommerce.inventoryservice.domain.ReservationStatus;
import com.ecommerce.inventoryservice.domain.StockReservation;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.dto.ReplenishStockRequest;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.StockReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @InjectMocks
    private InventoryService inventoryService;


    @Test
    @DisplayName("Пополнение склада: создание нового товара если его еще нет")
    void testReplenishStockReturnsInventoryResponse() {
        ReplenishStockRequest request = new ReplenishStockRequest("SKU-100", 50);

        when(inventoryRepository.findByStockId("SKU-100")).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        InventoryResponse response = inventoryService.replenishStock(request);

        assertThat(response.stockId()).isEqualTo("SKU-100");
        assertThat(response.quantity()).isEqualTo(50);
        assertThat(response.availableQuantity()).isEqualTo(50);
        verify(inventoryRepository).save(any(InventoryItem.class));
    }

    @Test
    @DisplayName("Пополнение склада: увеличение остатка уже существующего товара")
    void testReplenishStockReturnsInventoryResponseWithMoreDetails() {
        ReplenishStockRequest request = new ReplenishStockRequest("SKU-100", 50);
        InventoryItem existingItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .stockId("SKU-100")
                .quantity(50)
                .reservedQuantity(10)
                .build();

        when(inventoryRepository.findByStockId("SKU-100")).thenReturn(Optional.of(existingItem));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(existingItem);

        InventoryResponse response = inventoryService.replenishStock(request);

        assertThat(response.quantity()).isEqualTo(100);
        assertThat(response.reservedQuantity()).isEqualTo(10);
        assertThat(response.availableQuantity()).isEqualTo(90);
        verify(inventoryRepository).save(any(InventoryItem.class));
    }

    @Test
    @DisplayName("Получение остатков товара: если такой товар существует")
    void testGetStockReturnsInventoryResponseIfExists() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .stockId("SKU-100")
                .quantity(50)
                .reservedQuantity(10)
                .build();

        when(inventoryRepository.findByStockId("SKU-100")).thenReturn(Optional.of(item));

        InventoryResponse response = inventoryService.getStock("SKU-100");

        assertThat(response.stockId()).isEqualTo("SKU-100");
        assertThat(response.availableQuantity()).isEqualTo(40);
    }

    @Test
    @DisplayName("Получение остатков товара: NoSuchElementException если такого нет")
    void testGetStockReturnsNoSuchElementExceptionIfNotExists() {
        when(inventoryRepository.findByStockId("SKU-100")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getStock("SKU-100"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Stock item not found for stockId=SKU-100");
    }

    @Test
    @DisplayName("Резервирование (gRPC): успешная бронь при наличии достаточного количества")
    void testReserveStockSuccessIfEnoughStockAndExists() {
        String orderId = "order-123";
        StockItem stockItem = StockItem.newBuilder()
                .setStockId("SKU-100")
                .setQuantity(5)
                .build();

        InventoryItem inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .stockId("SKU-100")
                .quantity(10)
                .reservedQuantity(3) // Доступно получается 7 штук
                .build();

        when(inventoryRepository.findByStockIdWithLock("SKU-100")).thenReturn(Optional.of(inventoryItem));
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);

        boolean reserved = inventoryService.reserveStock(orderId, List.of(stockItem));

        assertThat(reserved).isTrue();
        assertThat(inventoryItem.getReservedQuantity()).isEqualTo(8); // 3 + 5

        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(stockReservationRepository).save(captor.capture());
        StockReservation reservation = captor.getValue();

        assertThat(reservation.getOrderId()).isEqualTo(orderId);
        assertThat(reservation.getStockId()).isEqualTo("SKU-100");
        assertThat(reservation.getQuantity()).isEqualTo(5);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    @DisplayName("Резервирование (gRPC): отказ если кол-во больше или нет такого товара")
    void testReserveStockFailedIfNotEnoughStockAndNotExists() {
        String orderId = "order-123";
        StockItem stockItem = StockItem.newBuilder()
                .setStockId("SKU-100")
                .setQuantity(10)
                .build();

        InventoryItem inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .stockId("SKU-100")
                .quantity(10)
                .reservedQuantity(3) // Доступно получается 7 штук
                .build();

        when(inventoryRepository.findByStockIdWithLock("SKU-100")).thenReturn(Optional.of(inventoryItem));

        boolean reserved = inventoryService.reserveStock(orderId, List.of(stockItem));

        assertThat(reserved).isFalse();
        assertThat(inventoryItem.getReservedQuantity()).isEqualTo(3); // не изменилось


        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    @DisplayName("SAGA: успешное снятие брони товара при отмене заказа")
    void testReleaseStockSuccessful() {
        String orderId = "order-123";
        StockReservation reservation = StockReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .stockId("SKU-100")
                .quantity(5)
                .status(ReservationStatus.RESERVED)
                .build();

        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .stockId("SKU-100")
                .quantity(10)
                .reservedQuantity(7)
                .build();

        when(stockReservationRepository.findAllByOrderIdAndStatus(orderId, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(inventoryRepository.findByStockIdWithLock("SKU-100"))
                .thenReturn(Optional.of(item));

        inventoryService.releaseStock(orderId);

        assertThat(item.getReservedQuantity()).isEqualTo(2);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        verify(stockReservationRepository).save(any(StockReservation.class));
        verify(inventoryRepository).save(any(InventoryItem.class));
    }
}
