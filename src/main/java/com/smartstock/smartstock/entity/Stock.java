package com.smartstock.smartstock.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks",
        // Composite unique constraint — one product per warehouse
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"warehouse_id", "product_id"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many stocks belong to one warehouse
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // Many stocks belong to one product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Alert when quantity drops below this
    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;

    // Alert when quantity goes above this
    @Column(name = "max_quantity", nullable = false)
    private Integer maxQuantity;

    // Optimistic locking — prevents two people updating same stock simultaneously
    // Interview question: "How do you handle concurrent stock updates?"
    // Answer: "I use @Version for optimistic locking"
    @Version
    private Integer version;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}