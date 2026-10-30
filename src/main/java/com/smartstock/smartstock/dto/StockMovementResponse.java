
package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.StockMovement;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockMovementResponse {
    private Long id;
    private Long stockId;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productName;
    private String sku;
    private StockMovement.MovementType movementType;
    private Integer quantity;
    private Integer previousQuantity;
    private Integer newQuantity;
    private String referenceNumber;
    private Long sourceWarehouseId;
    private String sourceWarehouseName;
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private String reason;
    private String performedBy;
    private LocalDateTime createdAt;
}
