package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.StockMovement;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockMovementRequest {

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Movement type is required")
    private StockMovement.MovementType movementType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Adjustment direction is required")
    private AdjustmentDirection adjustmentDirection =
            AdjustmentDirection.INCREASE;

    private String reason;

    @jakarta.validation.constraints.Size(max = 120,
            message = "Reference number cannot exceed 120 characters")
    private String referenceNumber;

    public enum AdjustmentDirection {
        INCREASE,
        DECREASE
    }
}