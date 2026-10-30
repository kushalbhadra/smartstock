package com.smartstock.smartstock.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_recipient_condition",
                columnNames = {"recipient_id", "condition_key"}
        ),
        indexes = {
                @Index(name = "idx_notifications_recipient_recent",
                        columnList = "recipient_id,last_occurred_at"),
                @Index(name = "idx_notifications_recipient_unread",
                        columnList = "recipient_id,read_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Severity severity;

    @Column(name = "condition_key", nullable = false, length = 160)
    private String conditionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_occurred_at", nullable = false)
    private LocalDateTime lastOccurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Type {
        LOW_STOCK,
        OUT_OF_STOCK,
        OVERSTOCK,
        WAREHOUSE_NEAR_CAPACITY,
        WAREHOUSE_CAPACITY_FULL,
        WAREHOUSE_CAPACITY_EXCEEDED,
        FORECAST_ANOMALY,
        PURCHASE_ORDER_APPROVED,
        PURCHASE_ORDER_PARTIALLY_RECEIVED,
        PURCHASE_ORDER_RECEIVED
    }

    public enum TargetType {
        STOCK,
        WAREHOUSE,
        PRODUCT,
        PURCHASE_ORDER
    }

    public enum Severity {
        CRITICAL,
        WARNING,
        INFO
    }
}
