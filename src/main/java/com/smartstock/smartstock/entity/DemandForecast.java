package com.smartstock.smartstock.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "demand_forecasts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "forecast_next_day")
    private Integer forecastNextDay;

    @Column(name = "forecast_next_week")
    private Integer forecastNextWeek;

    @Column(name = "forecast_next_month")
    private Integer forecastNextMonth;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected;

    @Column(length = 1000)
    private String recommendation;

    @Column(name = "suggested_reorder_quantity")
    private Integer suggestedReorderQuantity;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "forecast_status", nullable = false, length = 32)
    private ForecastStatus status = ForecastStatus.LEGACY;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "historical_demand_json", columnDefinition = "text")
    private String historicalDemandJson;

    @Column(name = "daily_predictions_json", columnDefinition = "text")
    private String dailyPredictionsJson;

    @Column(name = "accuracy_mae", precision = 14, scale = 4)
    private BigDecimal accuracyMae;

    @Column(name = "accuracy_wape", precision = 16, scale = 6)
    private BigDecimal accuracyWape;

    @Column(name = "accuracy_days")
    private Integer accuracyDays;

    @Column(name = "accuracy_comparison_json", columnDefinition = "text")
    private String accuracyComparisonJson;

    @Column(name = "accuracy_evaluated_at")
    private LocalDateTime accuracyEvaluatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ForecastStatus {
        SUCCESS, INSUFFICIENT_DATA, FAILED, LEGACY
    }
}