package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    // Find all stock in a specific warehouse
    List<Stock> findByWarehouseId(Long warehouseId);

    // Find stock for a specific product in a specific warehouse
    Optional<Stock> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM Stock s
            WHERE s.warehouse.id = :warehouseId
              AND s.product.id = :productId
            """)
    Optional<Stock> findByWarehouseIdAndProductIdForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId);

    // Find all stocks where quantity is below minimum
    // Used to reconcile low-stock notifications for a warehouse.
    @Query("SELECT s FROM Stock s WHERE s.quantity < s.minQuantity")
    List<Stock> findLowStockItems();

    @Query("""
            SELECT s FROM Stock s
            WHERE s.quantity < s.minQuantity
              AND s.warehouse.id = :warehouseId
            """)
    List<Stock> findLowStockItemsByWarehouseId(
            @Param("warehouseId") Long warehouseId);

    // Find critical stocks (below 50% of minimum)
    @Query("SELECT s FROM Stock s WHERE s.quantity < (s.minQuantity * 0.5)")
    List<Stock> findCriticalStockItems();

    // Find all stocks for a specific product across all warehouses
    List<Stock> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("""
            SELECT COALESCE(SUM(s.quantity * s.product.storageCapacityFactor), 0)
            FROM Stock s
            WHERE s.warehouse.id = :warehouseId
            """)
    BigDecimal sumStorageCapacityByWarehouseId(
            @Param("warehouseId") Long warehouseId);

    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM Stock s
            WHERE s.warehouse.id = :warehouseId
            """)
    long sumQuantityByWarehouseId(@Param("warehouseId") Long warehouseId);

    long countByWarehouseId(Long warehouseId);

    boolean existsByWarehouseId(Long warehouseId);
}