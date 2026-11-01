package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.DashboardSummaryResponse;
import com.smartstock.smartstock.dto.DashboardSummaryResponse.ForecastPreview;
import com.smartstock.smartstock.dto.DashboardSummaryResponse.MovementDay;
import com.smartstock.smartstock.dto.DashboardSummaryResponse.PurchaseOrderStatusCount;
import com.smartstock.smartstock.dto.DashboardSummaryResponse.TopMovingProduct;
import com.smartstock.smartstock.dto.DashboardSummaryResponse.WarehouseStock;
import com.smartstock.smartstock.dto.PurchaseOrderResponse;
import com.smartstock.smartstock.dto.StockMovementResponse;
import com.smartstock.smartstock.dto.StockResponse;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private static final int PREVIEW_LIMIT = 5;

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DemandForecastRepository forecastRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final NotificationService notificationService;
    private final boolean managerApprovalEnabled;

    public DashboardService(
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            StockRepository stockRepository,
            StockMovementRepository movementRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            DemandForecastRepository forecastRepository,
            UserRepository userRepository,
            StockService stockService,
            NotificationService notificationService,
            @Value("${app.permissions.manager-approve-purchase-orders:false}")
            boolean managerApprovalEnabled) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.forecastRepository = forecastRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.notificationService = notificationService;
        this.managerApprovalEnabled = managerApprovalEnabled;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user was not found."));
        boolean manager = user.getRole() == User.Role.WAREHOUSE_MGR;
        Long warehouseId = manager && user.getWarehouse() != null
                ? user.getWarehouse().getId() : null;
        boolean noAssignedWarehouse = manager && warehouseId == null;

        long stockUnits = noAssignedWarehouse ? 0
                : manager
                ? stockRepository.sumQuantityByWarehouseId(warehouseId)
                : stockRepository.sumQuantity();
        long lowStock = noAssignedWarehouse ? 0
                : manager
                ? stockRepository.countLowStockItemsByWarehouseId(
                        warehouseId)
                : stockRepository.countLowStockItems();
        long outOfStock = noAssignedWarehouse ? 0
                : manager
                ? stockRepository.countByWarehouseIdAndQuantity(
                        warehouseId, 0)
                : stockRepository.countByQuantity(0);
        long overstock = noAssignedWarehouse ? 0
                : manager
                ? stockRepository.countOverstockItemsByWarehouseId(warehouseId)
                : stockRepository.countOverstockItems();

        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        long movementsToday = noAssignedWarehouse ? 0
                : manager
                ? movementRepository
                        .countByStock_Warehouse_IdAndCreatedAtAfter(
                                warehouseId, startOfToday)
                : movementRepository.countByCreatedAtAfter(startOfToday);

        List<WarehouseStock> warehouseDistribution =
                warehouseDistribution(manager, warehouseId);
        List<PurchaseOrderStatusCount> statusCounts =
                purchaseOrderStatusCounts(manager, warehouseId);
        long pendingOrders = statusCounts.stream()
                .filter(item -> item.status() == PurchaseOrder.Status.PENDING)
                .mapToLong(PurchaseOrderStatusCount::count)
                .findFirst().orElse(0);
        long totalWarehouses = manager
                ? warehouseId == null ? 0 : 1
                : warehouseRepository.count();
        String warehouseName = manager && user.getWarehouse() != null
                ? user.getWarehouse().getName() : null;

        List<StockMovementResponse> recentMovements =
                stockService.getRecentMovements(email).stream()
                        .limit(PREVIEW_LIMIT)
                        .toList();
        List<PurchaseOrder> recentOrders = noAssignedWarehouse
                ? List.of()
                : manager
                ? purchaseOrderRepository
                        .findTop5ByWarehouseIdOrderByCreatedAtDesc(warehouseId)
                : purchaseOrderRepository.findTop5ByOrderByCreatedAtDesc();
        List<Stock> lowStockItems = noAssignedWarehouse
                ? List.of()
                : manager
                ? stockRepository.findTopLowStockItemsByWarehouseId(
                        warehouseId, PageRequest.of(0, PREVIEW_LIMIT))
                : stockRepository.findTopLowStockItems(
                        PageRequest.of(0, PREVIEW_LIMIT));

        LocalDateTime trendStart = today.minusDays(6).atStartOfDay();
        List<MovementDay> movementTrend = movementTrend(
                manager, warehouseId, trendStart,
                today.plusDays(1).atStartOfDay(), today);
        LocalDateTime movingProductsStart = today.minusDays(29).atStartOfDay();
        List<Object[]> movingProducts = noAssignedWarehouse
                ? List.of()
                : manager
                ? movementRepository.getTopMovingProductsByWarehouseId(
                        warehouseId, movingProductsStart,
                        PageRequest.of(0, PREVIEW_LIMIT))
                : movementRepository.getTopMovingProducts(
                        movingProductsStart, PageRequest.of(0, PREVIEW_LIMIT));

        List<DemandForecast> recentForecasts = noAssignedWarehouse
                ? List.of()
                : manager
                ? forecastsForWarehouse(warehouseId)
                : forecastRepository.findTop5ByOrderByCreatedAtDesc();
        List<ForecastPreview> forecasts = recentForecasts.stream()
                .map(this::toForecastPreview)
                .toList();

        return new DashboardSummaryResponse(
                user.getRole(),
                warehouseName,
                user.getRole() == User.Role.ADMIN
                        || manager && managerApprovalEnabled,
                productRepository.count(),
                totalWarehouses,
                stockUnits,
                lowStock,
                outOfStock,
                overstock,
                pendingOrders,
                movementsToday,
                notificationService.getUnreadCount(email),
                warehouseDistribution,
                movementTrend,
                statusCounts,
                movingProducts.stream().map(this::toTopMovingProduct).toList(),
                recentMovements,
                recentOrders.stream().map(PurchaseOrderResponse::from).toList(),
                lowStockItems.stream().map(this::toStockResponse).toList(),
                forecasts,
                notificationService.getRecent(email, PREVIEW_LIMIT));
    }

    private List<WarehouseStock> warehouseDistribution(
            boolean manager, Long warehouseId) {
        Map<Long, Long> totals = new HashMap<>();
        if (manager && warehouseId == null) {
            return List.of();
        }
        List<Object[]> rows = manager
                ? stockRepository.getWarehouseStockDistributionByWarehouseId(
                        warehouseId)
                : stockRepository.getWarehouseStockDistribution();
        for (Object[] row : rows) {
            totals.put(((Number) row[0]).longValue(),
                    ((Number) row[2]).longValue());
        }
        List<Warehouse> warehouses = manager
                ? warehouseRepository.findById(warehouseId).stream().toList()
                : warehouseRepository.findAll();
        return warehouses.stream()
                .map(warehouse -> new WarehouseStock(
                        warehouse.getId(),
                        warehouse.getName(),
                        totals.getOrDefault(warehouse.getId(), 0L)))
                .toList();
    }

    private List<DemandForecast> forecastsForWarehouse(Long warehouseId) {
        List<Long> productIds =
                stockRepository.findProductIdsByWarehouseId(warehouseId);
        return productIds.isEmpty()
                ? List.of()
                : forecastRepository.findTop5ByProductIdInOrderByCreatedAtDesc(
                        productIds);
    }

    private List<PurchaseOrderStatusCount> purchaseOrderStatusCounts(
            boolean manager, Long warehouseId) {
        EnumMap<PurchaseOrder.Status, Long> counts =
                new EnumMap<>(PurchaseOrder.Status.class);
        for (PurchaseOrder.Status status : PurchaseOrder.Status.values()) {
            counts.put(status, 0L);
        }
        if (manager && warehouseId == null) {
            return statusCountList(counts);
        }
        List<Object[]> rows = manager
                ? purchaseOrderRepository.getStatusCountsByWarehouseId(
                        warehouseId)
                : purchaseOrderRepository.getStatusCounts();
        for (Object[] row : rows) {
            counts.put((PurchaseOrder.Status) row[0],
                    ((Number) row[1]).longValue());
        }
        return statusCountList(counts);
    }

    private List<PurchaseOrderStatusCount> statusCountList(
            EnumMap<PurchaseOrder.Status, Long> counts) {
        return List.of(PurchaseOrder.Status.values()).stream()
                .map(status -> new PurchaseOrderStatusCount(
                        status, counts.get(status)))
                .toList();
    }

    private List<MovementDay> movementTrend(
            boolean manager,
            Long warehouseId,
            LocalDateTime start,
            LocalDateTime end,
            LocalDate today) {
        Map<LocalDate, MovementDay> totals = new HashMap<>();
        if (manager && warehouseId == null) {
            return emptyMovementTrend(start.toLocalDate(), today);
        }
        List<Object[]> rows = manager
                ? movementRepository.getMovementTrendsByWarehouseId(
                        warehouseId, start, end)
                : movementRepository.getMovementTrends(start, end);
        for (Object[] row : rows) {
            LocalDate date = LocalDate.parse(row[0].toString().substring(0, 10));
            totals.put(date, new MovementDay(
                    date, ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()));
        }
        List<MovementDay> days = new ArrayList<>();
        for (LocalDate day = start.toLocalDate(); !day.isAfter(today);
             day = day.plusDays(1)) {
            days.add(totals.getOrDefault(day, new MovementDay(day, 0, 0)));
        }
        return days;
    }

    private List<MovementDay> emptyMovementTrend(
            LocalDate start, LocalDate today) {
        List<MovementDay> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new MovementDay(day, 0, 0));
        }
        return days;
    }

    private TopMovingProduct toTopMovingProduct(Object[] row) {
        return new TopMovingProduct(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue());
    }

    private StockResponse toStockResponse(Stock stock) {
        return StockResponse.builder()
                .id(stock.getId())
                .warehouseId(stock.getWarehouse().getId())
                .warehouseName(stock.getWarehouse().getName())
                .productId(stock.getProduct().getId())
                .productName(stock.getProduct().getName())
                .sku(stock.getProduct().getSku())
                .quantity(stock.getQuantity())
                .minQuantity(stock.getMinQuantity())
                .maxQuantity(stock.getMaxQuantity())
                .version(stock.getVersion())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    private ForecastPreview toForecastPreview(DemandForecast forecast) {
        return new ForecastPreview(
                forecast.getId(),
                forecast.getProduct().getId(),
                forecast.getProduct().getName(),
                forecast.getProduct().getSku(),
                forecast.getForecastNextWeek(),
                forecast.getForecastNextMonth(),
                forecast.getSuggestedReorderQuantity(),
                forecast.getAnomalyDetected(),
                forecast.getCreatedAt());
    }
}
