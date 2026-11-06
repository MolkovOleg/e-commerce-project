package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.inventoryservice.domain.ReservationStatus;
import com.ecommerce.inventoryservice.domain.StockReservation;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.dto.ReplenishStockRequest;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grpc.server.port=0")
@Testcontainers
class InventoryServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrency: 10 параллельных потоков резервиурет по 2 шт. из 10 - ровно 5 успещных благодаря SELECT FOR UPDATE")
    void concurrentReserveStockShouldPreventOversellingWithPessimisticLock() throws InterruptedException {
        String stockId = "SKU-CONCURRENT-1";
        inventoryService.replenishStock(new ReplenishStockRequest(stockId, 10));

        int threadCount = 10;
        int quantityPerOrder = 2;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    StockItem item = StockItem.newBuilder()
                            .setStockId(stockId)
                            .setQuantity(quantityPerOrder)
                            .build();

                    boolean reserved = inventoryService.reserveStock(UUID.randomUUID().toString(), List.of(item));
                    if (reserved) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Ждем готовности 10 потоков и даем одновременный старт
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Ровно 5 заказов должны зарезервировать по 2 шт. (5 * 2 = 10), а 5 - получить отказ
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        InventoryResponse finalStock = inventoryService.getStock(stockId);
        assertThat(finalStock.quantity()).isEqualTo(10);
        assertThat(finalStock.reservedQuantity()).isEqualTo(10);
        assertThat(finalStock.availableQuantity()).isZero();
    }

    @Test
    @DisplayName("SAGA: снятие резерва и возврат доступного остатка. Перевод брони в статус RELEASED")
    void releaseStockShouldRestoreAvailableQuantityAndUpdateReservationStatus() {
        String stockId = "SKU-SAGA-1";
        String orderId = UUID.randomUUID().toString();
        inventoryService.replenishStock(new ReplenishStockRequest(stockId, 5));

        StockItem item = StockItem.newBuilder()
                .setStockId(stockId)
                .setQuantity(3)
                .build();

        boolean reserved = inventoryService.reserveStock(orderId, List.of(item));
        assertThat(reserved).isTrue();

        InventoryResponse afterResponse = inventoryService.getStock(stockId);
        assertThat(afterResponse.reservedQuantity()).isEqualTo(3);
        assertThat(afterResponse.availableQuantity()).isEqualTo(2);

        // Компенсирующая транзакция SAGA
        inventoryService.releaseStock(orderId);
        InventoryResponse afterRelease = inventoryService.getStock(stockId);
        assertThat(afterRelease.reservedQuantity()).isZero();
        assertThat(afterRelease.availableQuantity()).isEqualTo(5);

        List<StockReservation> releasedReservation = stockReservationRepository
                .findAllByOrderIdAndStatus(orderId, ReservationStatus.RELEASED);
        assertThat(releasedReservation).hasSize(1);

    }
}
