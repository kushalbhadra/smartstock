
package com.smartstock.smartstock.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockResponse {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productName;
    private String sku;
    private Integer quantity;
    private Integer minQuantity;
    private Integer maxQuantity;
    private Integer version;
    private LocalDateTime updatedAt;
}
