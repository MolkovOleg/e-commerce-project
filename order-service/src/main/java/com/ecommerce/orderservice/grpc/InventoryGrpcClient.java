package com.ecommerce.orderservice.grpc;

import com.ecommerce.common.grpc.inventory.CheckStockRequest;
import com.ecommerce.common.grpc.inventory.CheckStockResponse;
import com.ecommerce.common.grpc.inventory.InventoryServiceGrpc;
import com.ecommerce.common.grpc.inventory.ReleaseStockRequest;
import com.ecommerce.common.grpc.inventory.ReleaseStockResponse;
import com.ecommerce.common.grpc.inventory.ReserveStockRequest;
import com.ecommerce.common.grpc.inventory.ReserveStockResponse;
import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.orderservice.domain.OrderItem;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class InventoryGrpcClient {

    private static final long DEFAULT_TIMEOUT_SECONDS = 3;

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    /**
     * Проверка наличия товара на складе
     */
    public boolean checkStock(String stockId) {
        log.debug("gRPC Client: CheckStock for stockId={}", stockId);
        try {
            CheckStockRequest request = CheckStockRequest.newBuilder()
                    .setStockId(stockId)
                    .build();

            CheckStockResponse response = stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .checkStock(request);

            return response.getInStock();
        } catch (StatusRuntimeException ex) {
            log.error("gRPC Client: CheckStock for stockId={}, status={} failed: ", stockId, ex.getStatus());
            return false;
        }
    }

    /**
     * Атомарное резервирование позиции товара на складе
     * Возращает true, если все товары забронированы и false при нехватке или ошибке
     */
    public boolean reserveStock(String orderId, List<OrderItem> items) {
        log.info("gRPC Client: Reserve orderId={}, itemsCount={}", orderId, items.size());

        List<StockItem> protoItems = items.stream()
                .map(item -> StockItem.newBuilder()
                        .setStockId(item.getStockId())
                        .setQuantity(item.getQuantity())
                        .build())
                .toList();

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId(orderId)
                .addAllItems(protoItems)
                .build();

        try {
            ReserveStockResponse response = stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .reserveStock(request);

            if (!response.getSuccess()) {
                log.warn("gRPC Client: Stock reservation rejected for orderId={}, message={}",
                        orderId, response.getMessage());
            }

            return response.getSuccess();
        } catch (StatusRuntimeException ex) {
            log.error("gRPC Client: ReserveStock error for orderId={}, status={}", orderId, ex.getStatus());
            return false;
        }
    }

    public void releaseStock(String orderId) {
        log.info("gRPC Client: Releasing stock for orderId={}", orderId);

        ReleaseStockRequest request = ReleaseStockRequest.newBuilder()
                .setOrderId(orderId)
                .build();

        try {
            ReleaseStockResponse response = stub
                    .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .releaseStock(request);

            if (response.getSuccess()) {
                log.info("gRPC Client: Stock released successfully for orderId={}", orderId);
            } else {
                log.error("gRPC Client: Failed to release stock for orderId={}, message={}",
                        orderId, response.getMessage());
            }
        } catch (StatusRuntimeException ex) {
            log.error("gRPC Client: Critical error during SAGA ReleaseStock for orderId={}, status={}",
                    orderId, ex.getStatus(), ex);
        }
    }
}
