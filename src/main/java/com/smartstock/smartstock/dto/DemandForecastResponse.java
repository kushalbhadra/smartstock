package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.DemandForecast;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DemandForecastResponse(
        Long id,
        ProductSummary product,
        Long warehouseId,
        String warehouseName,
        Integer forecastNextDay,
        Integer forecastNextWeek,
        Integer forecastNextMonth,
        BigDecimal confidenceScore,
        Boolean anomalyDetected,
        String recommendation,
        Integer suggestedReorderQuantity,
        DemandForecast.ForecastStatus status,
        String statusMessage,
        String modelName,
        List<ForecastDataPoint> historicalDemand,
        List<ForecastDataPoint> dailyPredictions,
        BigDecimal accuracyMae,
        BigDecimal accuracyWape,
        Integer accuracyDays,
        List<ForecastAccuracyPoint> accuracyComparison,
        LocalDateTime createdAt) {

    public record ProductSummary(
            Long id,
            String name,
            String sku,
            String category,
            String unit,
            BigDecimal price,
            BigDecimal storageCapacityFactor,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
