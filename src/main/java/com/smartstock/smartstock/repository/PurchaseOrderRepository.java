package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findByStatus(PurchaseOrder.Status status);

    List<PurchaseOrder> findBySupplierId(Long supplierId);

    boolean existsByProductId(Long productId);

    @Query("""
            select distinct po.product.id from PurchaseOrder po
            where po.product.id in :productIds
            """)
    List<Long> findProductIdsWithPurchaseOrders(
            @Param("productIds") List<Long> productIds);

    boolean existsBySupplierId(Long supplierId);

    List<PurchaseOrder> findByWarehouseId(Long warehouseId);

    List<PurchaseOrder> findByCreatedById(Long userId);

    boolean existsByCreatedById(Long userId);

    boolean existsByWarehouseIdAndStatusIn(
            Long warehouseId, List<PurchaseOrder.Status> statuses);

    // Count pending orders — for dashboard
    long countByStatus(PurchaseOrder.Status status);

    long countByWarehouseIdAndStatus(
            Long warehouseId, PurchaseOrder.Status status);

    List<PurchaseOrder> findTop5ByOrderByCreatedAtDesc();

    List<PurchaseOrder> findTop5ByWarehouseIdOrderByCreatedAtDesc(
            Long warehouseId);

    @Query("""
            SELECT p.status, COUNT(p)
            FROM PurchaseOrder p
            GROUP BY p.status
            """)
    List<Object[]> getStatusCounts();

    @Query("""
            SELECT p.status, COUNT(p)
            FROM PurchaseOrder p
            WHERE p.warehouse.id = :warehouseId
            GROUP BY p.status
            """)
    List<Object[]> getStatusCountsByWarehouseId(
            @Param("warehouseId") Long warehouseId);
}