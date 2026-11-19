package com.smartstock.smartstock.config.batch;

import com.smartstock.smartstock.dto.ForecastDataPoint;

import java.time.LocalDate;
import java.util.List;

public record ProductForecastData(
        Long productId,
        String productName,
        String category,
        Long warehouseId,
        String warehouseName,
        LocalDate asOfDate,
        int currentStock,
        int historyCoverageDays,
        List<ForecastDataPoint> historicalDemand) {
}
