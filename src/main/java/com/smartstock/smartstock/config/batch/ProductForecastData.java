package com.smartstock.smartstock.config.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Data passed from Reader to Processor
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductForecastData {

    private Long productId;
    private String productName;
    private String category;
    private int currentStock;
    private int outboundLast7Days;
    private int outboundLast30Days;
}