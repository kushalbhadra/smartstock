package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    // Find all stock in a specific warehouse
    List<Stock> findByWarehouseId(Long warehouseId);

    // Find stock for a specific product in a specific warehouse
    Optional<Stock> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    // Find all stocks where quantity is below minimum
    // This is used for WhatsApp low stock alerts
    @Query("SELECT s FROM Stock s WHERE s.quantity < s.minQuantity")
    List<Stock> findLowStockItems();

    // Find critical stocks (below 50% of minimum)
    @Query("SELECT s FROM Stock s WHERE s.quantity < (s.minQuantity * 0.5)")
    List<Stock> findCriticalStockItems();

    // Find all stocks for a specific product across all warehouses
    List<Stock> findByProductId(Long productId);
}