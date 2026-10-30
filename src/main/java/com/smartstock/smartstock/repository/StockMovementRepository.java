package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByPerformedById(Long userId);

    // Last 10 movements — for dashboard
    List<StockMovement> findTop10ByOrderByCreatedAtDesc();

    List<StockMovement>
    findTop10ByStock_Warehouse_IdOrderByCreatedAtDesc(Long warehouseId);

    @Query("""
            select m from StockMovement m
            where (:warehouseId is null
                   or m.stock.warehouse.id = :warehouseId)
              and (:type is null or m.movementType = :type)
              and (:start is null or m.createdAt >= :start)
              and (:end is null or m.createdAt <= :end)
              and (:search is null
                   or lower(m.stock.product.name)
                        like lower(concat('%', :search, '%'))
                   or lower(m.stock.product.sku)
                        like lower(concat('%', :search, '%'))
                   or lower(coalesce(m.referenceNumber, ''))
                        like lower(concat('%', :search, '%')))
            """)
    Page<StockMovement> searchHistory(
            @Param("warehouseId") Long warehouseId,
            @Param("type") StockMovement.MovementType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select m from StockMovement m
            where (:warehouseId is null
                   or m.stock.warehouse.id = :warehouseId)
              and (:type is null or m.movementType = :type)
              and (:start is null or m.createdAt >= :start)
              and (:end is null or m.createdAt <= :end)
              and (:search is null
                   or lower(m.stock.product.name)
                        like lower(concat('%', :search, '%'))
                   or lower(m.stock.product.sku)
                        like lower(concat('%', :search, '%'))
                   or lower(coalesce(m.referenceNumber, ''))
                        like lower(concat('%', :search, '%')))
            order by m.createdAt desc
            """)
    List<StockMovement> searchHistoryForExport(
            @Param("warehouseId") Long warehouseId,
            @Param("type") StockMovement.MovementType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search);
}