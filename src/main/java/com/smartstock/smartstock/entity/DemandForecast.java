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

    // AI predicted units needed next week
    @Column(name = "forecast_next_week")
    private Integer forecastNextWeek;

    // AI predicted units needed next month
    @Column(name = "forecast_next_month")
    private Integer forecastNextMonth;

    // How confident the AI is — 0.0 to 1.0
    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    // Did AI detect unusual pattern?
    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected;

    // AI's text recommendation
    @Column(length = 1000)
    private String recommendation;

    // How many units to reorder
    @Column(name = "suggested_reorder_quantity")
    private Integer suggestedReorderQuantity;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}