package com.ecommerce.inventoryservice.grpc;

import com.ecommerce.common.grpc.inventory.CheckStockRequest;
import com.ecommerce.common.grpc.inventory.CheckStockResponse;
import com.ecommerce.common.grpc.inventory.InventoryServiceGrpc;
import com.ecommerce.common.grpc.inventory.ReleaseStockRequest;
import com.ecommerce.common.grpc.inventory.ReleaseStockResponse;
import com.ecommerce.common.grpc.inventory.ReserveStockRequest;
import com.ecommerce.common.grpc.inventory.ReserveStockResponse;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.NoSuchElementException;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService  inventoryService;

    /**
     * Проверка доступности товара по gRPC
     */
    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        String stockId = request.getStockId();
        log.info("gRPC call: CheckStock for stockId={}", stockId);

        try {
            InventoryResponse response = inventoryService.getStock(stockId);
            boolean inStock = response.availableQuantity() > 0;

            CheckStockResponse grpcResponse = CheckStockResponse.newBuilder()
                    .setStockId(stockId)
                    .setAvailableQuantity(response.availableQuantity())
                    .setInStock(inStock)
                    .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (NoSuchElementException ex) {
            log.error("gRPC CheckStock error for stockId {}", stockId, ex);
            responseObserver.onError(ex);
        }
    }

    /**
     * Атомарное резервирование товара (с пессимистичной блокировкой)
     */
    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        String orderId = request.getOrderId();
        log.info("gRPC call: ReserveStock for orderId={}, itemsCount={}", orderId, request.getItemsCount());

        try {
            boolean success = inventoryService.reserveStock(orderId, request.getItemsList());

            ReserveStockResponse grpcResponse = ReserveStockResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(success)
                    .setMessage(success
                            ? "Stock reserved successfully"
                            : "Insufficient stock or item not found")
                    .build();
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("gRPC ReserveStock error for orderId {}", orderId, ex);
            ReserveStockResponse grpcResponse = ReserveStockResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(false)
                    .setMessage("Reservation failed due to internal error: " + ex.getMessage())
                    .build();
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * SAGA компенсация: снятие резерва при отмене/сбое
     */
    @Override
    public void releaseStock(ReleaseStockRequest request, StreamObserver<ReleaseStockResponse> responseObserver) {
        String orderId = request.getOrderId();
        log.info("gRPC call: ReleaseStock for orderId={}", orderId);

        try {
            inventoryService.releaseStock(orderId);

            ReleaseStockResponse grpcResponse = ReleaseStockResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(true)
                    .setMessage("Stock released successfully")
                    .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (Exception ex)  {
            log.error("gRPC ReleaseStock error for orderId {}", orderId, ex);
            ReleaseStockResponse grpcResponse = ReleaseStockResponse.newBuilder()
                    .setOrderId(orderId)
                    .setSuccess(false)
                    .setMessage("ReleaseStock failed due to internal error: " + ex.getMessage())
                    .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        }
    }
}
