package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Recent movements for a stock entry
    List<StockMovement> findByStockIdOrderByCreatedAtDesc(Long stockId);

    // Movements within a date range
    @Query("SELECT m FROM StockMovement m WHERE m.createdAt BETWEEN :start AND :end")
    List<StockMovement> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // Movements by type (INBOUND/OUTBOUND/ADJUSTMENT)
    List<StockMovement> findByMovementType(StockMovement.MovementType type);

    // Last 10 movements — for dashboard
    List<StockMovement> findTop10ByOrderByCreatedAtDesc();
}