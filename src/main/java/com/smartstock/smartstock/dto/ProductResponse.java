package com.smartstock.smartstock.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// What we send back to React
@Data
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String category;
    private String unit;
    private BigDecimal price;
    private LocalDateTime createdAt;
}