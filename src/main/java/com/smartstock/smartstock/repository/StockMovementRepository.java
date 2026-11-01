package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends
        JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

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

    @Query("""
            select case when count(m) > 0 then true else false end
            from StockMovement m
            where m.stock.product.id = :productId
            """)
    boolean existsByProductIdInMovementHistory(
            @Param("productId") Long productId);

    // Last 10 movements — for dashboard
    List<StockMovement> findTop10ByOrderByCreatedAtDesc();

    List<StockMovement>
    findTop10ByStock_Warehouse_IdOrderByCreatedAtDesc(Long warehouseId);

    long countByCreatedAtAfter(LocalDateTime start);

    long countByStock_Warehouse_IdAndCreatedAtAfter(
            Long warehouseId, LocalDateTime start);

    @Query("""
            SELECT function('date_trunc', 'day', m.createdAt),
                   SUM(CASE WHEN m.movementType IN
                       (com.smartstock.smartstock.entity.StockMovement.MovementType.INBOUND,
                        com.smartstock.smartstock.entity.StockMovement.MovementType.TRANSFER_IN)
                       THEN m.quantity ELSE 0 END),
                   SUM(CASE WHEN m.movementType IN
                       (com.smartstock.smartstock.entity.StockMovement.MovementType.OUTBOUND,
                        com.smartstock.smartstock.entity.StockMovement.MovementType.TRANSFER_OUT)
                       THEN m.quantity ELSE 0 END)
            FROM StockMovement m
            WHERE m.createdAt >= :start
              AND m.createdAt < :end
            GROUP BY function('date_trunc', 'day', m.createdAt)
            ORDER BY function('date_trunc', 'day', m.createdAt)
            """)
    List<Object[]> getMovementTrends(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT function('date_trunc', 'day', m.createdAt),
                   SUM(CASE WHEN m.movementType IN
                       (com.smartstock.smartstock.entity.StockMovement.MovementType.INBOUND,
                        com.smartstock.smartstock.entity.StockMovement.MovementType.TRANSFER_IN)
                       THEN m.quantity ELSE 0 END),
                   SUM(CASE WHEN m.movementType IN
                       (com.smartstock.smartstock.entity.StockMovement.MovementType.OUTBOUND,
                        com.smartstock.smartstock.entity.StockMovement.MovementType.TRANSFER_OUT)
                       THEN m.quantity ELSE 0 END)
            FROM StockMovement m
            WHERE m.stock.warehouse.id = :warehouseId
              AND m.createdAt >= :start
              AND m.createdAt < :end
            GROUP BY function('date_trunc', 'day', m.createdAt)
            ORDER BY function('date_trunc', 'day', m.createdAt)
            """)
    List<Object[]> getMovementTrendsByWarehouseId(
            @Param("warehouseId") Long warehouseId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT m.stock.product.id, m.stock.product.name,
                   m.stock.product.sku, COUNT(m), SUM(m.quantity)
            FROM StockMovement m
            WHERE m.createdAt >= :start
            GROUP BY m.stock.product.id, m.stock.product.name,
                     m.stock.product.sku
            ORDER BY SUM(m.quantity) DESC, COUNT(m) DESC
            """)
    List<Object[]> getTopMovingProducts(
            @Param("start") LocalDateTime start,
            Pageable pageable);

    @Query("""
            SELECT m.stock.product.id, m.stock.product.name,
                   m.stock.product.sku, COUNT(m), SUM(m.quantity)
            FROM StockMovement m
            WHERE m.stock.warehouse.id = :warehouseId
              AND m.createdAt >= :start
            GROUP BY m.stock.product.id, m.stock.product.name,
                     m.stock.product.sku
            ORDER BY SUM(m.quantity) DESC, COUNT(m) DESC
            """)
    List<Object[]> getTopMovingProductsByWarehouseId(
            @Param("warehouseId") Long warehouseId,
            @Param("start") LocalDateTime start,
            Pageable pageable);

    @Query("""
            select distinct m.stock.product.id from StockMovement m
            where m.stock.product.id in :productIds
            """)
    List<Long> findProductIdsWithMovementHistory(
            @Param("productIds") List<Long> productIds);
}