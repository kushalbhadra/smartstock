package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock private ProductRepository productRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRepository movementRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private DemandForecastRepository forecastRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockService stockService;
    @Mock private NotificationService notificationService;

    @Test
    void managerDashboardUsesOnlyAssignedWarehouseAggregations() {
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Assigned warehouse")
                .build();
        User manager = User.builder()
                .id(7L)
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(warehouse)
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(warehouseRepository.findById(2L))
                .thenReturn(Optional.of(warehouse));
        when(stockService.getRecentMovements(manager.getEmail()))
                .thenReturn(List.of());
        when(purchaseOrderRepository
                .findTop5ByWarehouseIdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());
        when(stockRepository.findTopLowStockItemsByWarehouseId(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        var summary = new DashboardService(
                productRepository,
                warehouseRepository,
                stockRepository,
                movementRepository,
                purchaseOrderRepository,
                forecastRepository,
                userRepository,
                stockService,
                notificationService,
                false).getSummary(manager.getEmail());

        assertEquals(User.Role.WAREHOUSE_MGR, summary.role());
        assertEquals("Assigned warehouse", summary.assignedWarehouseName());
        assertEquals(1, summary.totalWarehouses());
        assertEquals(0, summary.totalStockUnits());
        verify(stockRepository).sumQuantityByWarehouseId(2L);
        verify(stockRepository).getWarehouseStockDistributionByWarehouseId(2L);
        verify(stockRepository, never()).sumQuantity();
        verify(stockRepository, never()).getWarehouseStockDistribution();
        verify(purchaseOrderRepository)
                .getStatusCountsByWarehouseId(2L);
        verify(purchaseOrderRepository, never()).getStatusCounts();
        verify(warehouseRepository, never()).findAll();
        verify(warehouseRepository, never()).count();
    }
}
