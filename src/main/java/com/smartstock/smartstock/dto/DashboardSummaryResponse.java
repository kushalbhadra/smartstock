package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardSummaryResponse(
        User.Role role,
        String assignedWarehouseName,
        boolean canApprovePurchaseOrders,
        long totalProducts,
        long totalWarehouses,
        long totalStockUnits,
        long lowStockItems,
        long outOfStockItems,
        long overstockItems,
        long pendingPurchaseOrders,
        long movementsToday,
        long unreadNotifications,
        List<WarehouseStock> warehouseDistribution,
        List<MovementDay> movementTrend,
        List<PurchaseOrderStatusCount> purchaseOrderStatuses,
        List<TopMovingProduct> topMovingProducts,
        List<StockMovementResponse> recentMovements,
        List<PurchaseOrderResponse> recentPurchaseOrders,
        List<StockResponse> lowStockPreview,
        List<ForecastPreview> forecasts,
        List<NotificationResponse> notifications
) {
    public record WarehouseStock(
            Long warehouseId, String warehouseName, long totalUnits) {
    }

    public record MovementDay(
            LocalDate date, long inbound, long outbound) {
    }

    public record PurchaseOrderStatusCount(
            PurchaseOrder.Status status, long count) {
    }

    public record TopMovingProduct(
            Long productId, String productName, String sku,
            long movementCount, long totalQuantity) {
    }

    public record ForecastPreview(
            Long id, Long productId, String productName, String sku,
            Integer forecastNextWeek, Integer forecastNextMonth,
            Integer suggestedReorderQuantity, Boolean anomalyDetected,
            LocalDateTime createdAt) {
    }
}
