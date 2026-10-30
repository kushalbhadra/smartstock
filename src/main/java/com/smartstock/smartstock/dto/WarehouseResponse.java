
package com.smartstock.smartstock.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Builder
public class WarehouseResponse {

    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    private BigDecimal usedCapacity;
    private BigDecimal remainingCapacity;
    private BigDecimal utilizationPercentage;
    private long productCount;
    private String capacityUnit;
    private LocalDateTime createdAt;
}
