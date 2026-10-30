
package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.dto.StockMovementResponse;
import com.smartstock.smartstock.dto.StockResponse;
import com.smartstock.smartstock.dto.StockTransferRequest;
import com.smartstock.smartstock.entity.*;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class StockService {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<StockResponse> getAllStocks(String email) {
        User user = findUser(email);
        List<Stock> stocks = user.getRole() == User.Role.WAREHOUSE_MGR
                ? user.getWarehouse() == null
                ? List.of()
                : stockRepository.findByWarehouseId(
                        user.getWarehouse().getId())
                : stockRepository.findAll();
        return stocks
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getStocksByWarehouse(
            Long warehouseId, String email) {
        User user = findUser(email);
        ensureWarehouseAccess(user, warehouseId);
        return stockRepository.findByWarehouseId(warehouseId)
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getLowStockItems(String email) {
        User user = findUser(email);
        List<Stock> stocks = user.getRole() == User.Role.WAREHOUSE_MGR
                ? user.getWarehouse() == null
                ? List.of()
                : stockRepository.findLowStockItemsByWarehouseId(
                        user.getWarehouse().getId())
                : stockRepository.findLowStockItems();
        return stocks
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getRecentMovements(String email) {
        User user = findUser(email);
        List<StockMovement> movements =
                user.getRole() == User.Role.WAREHOUSE_MGR
                ? user.getWarehouse() == null
                ? List.of()
                : movementRepository
                        .findTop10ByStock_Warehouse_IdOrderByCreatedAtDesc(
                                user.getWarehouse().getId())
                : movementRepository.findTop10ByOrderByCreatedAtDesc();
        return movements.stream().map(this::toMovementResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovementHistory(
            String email,
            StockMovement.MovementType type,
            Long requestedWarehouseId,
            LocalDate from,
            LocalDate to,
            String requestedSearch,
            int requestedPage,
            int requestedSize) {
        User user = findUser(email);
        Long warehouseId = requestedWarehouseId;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            if (user.getWarehouse() == null) {
                Pageable pageable = PageRequest.of(
                        Math.max(0, requestedPage),
                        Math.max(1, Math.min(requestedSize, 100)));
                return Page.empty(pageable);
            }
            warehouseId = user.getWarehouse().getId();
        }
        int page = Math.max(0, requestedPage);
        int size = requestedSize < 1 ? 20
                : Math.min(requestedSize, 100);
        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.atTime(LocalTime.MAX);
        String search = requestedSearch == null
                || requestedSearch.isBlank()
                ? null : requestedSearch.trim();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(
                    "The start date must be on or before the end date.");
        }
        return movementRepository.searchHistory(
                        warehouseId,
                        type,
                        start,
                        end,
                        search,
                        pageable)
                .map(this::toMovementResponse);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsForExport(
            String email,
            StockMovement.MovementType type,
            Long requestedWarehouseId,
            LocalDate from,
            LocalDate to,
            String requestedSearch) {
        User user = findUser(email);
        Long warehouseId = requestedWarehouseId;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            warehouseId = user.getWarehouse() == null
                    ? -1L : user.getWarehouse().getId();
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(
                    "The start date must be on or before the end date.");
        }
        String search = requestedSearch == null
                || requestedSearch.isBlank()
                ? null : requestedSearch.trim();
        return movementRepository.searchHistoryForExport(
                warehouseId,
                type,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.atTime(LocalTime.MAX),
                search);
    }

    public StockMovementResponse recordMovement(
            StockMovementRequest request,
            Long performedByUserId) {

        User performedBy = userRepository.findById(performedByUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User", "id", performedByUserId));
        ensureWarehouseAccess(performedBy, request.getWarehouseId());
        productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "id", request.getProductId()));
        Warehouse warehouse = warehouseRepository
                .findByIdForUpdate(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", request.getWarehouseId()));
        if (request.getMovementType()
                == StockMovement.MovementType.TRANSFER_IN
                || request.getMovementType()
                == StockMovement.MovementType.TRANSFER_OUT) {
            throw new BusinessException(
                    "Use the stock transfer operation for transfer movements.");
        }
        Stock stock = stockRepository
                .findByWarehouseIdAndProductIdForUpdate(
                        request.getWarehouseId(),
                        request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock not found for this product in this warehouse"));

        int previousQuantity = stock.getQuantity();
        int delta = calculateDelta(
                request.getMovementType(),
                request.getQuantity(),
                request.getAdjustmentDirection());

        long newQuantity = (long) previousQuantity + delta;

        if ((request.getMovementType()
                == StockMovement.MovementType.INBOUND
                || request.getMovementType()
                == StockMovement.MovementType.ADJUSTMENT
                && request.getAdjustmentDirection()
                == StockMovementRequest.AdjustmentDirection.INCREASE)
                && newQuantity > stock.getMaxQuantity()) {
            long availableCapacity = Math.max(
                    0L,
                    (long) stock.getMaxQuantity() - stock.getQuantity());
            String operation = request.getMovementType()
                    == StockMovement.MovementType.INBOUND
                    ? "Incoming stock exceeds the maximum allowed."
                    : "Adjustment increase exceeds the maximum allowed.";
            String requestedLabel = request.getMovementType()
                    == StockMovement.MovementType.INBOUND
                    ? "Requested incoming quantity: "
                    : "Requested adjustment quantity: ";
            throw new BusinessException(
                    operation + " Current stock: " + previousQuantity
                            + " units; Maximum stock: "
                            + stock.getMaxQuantity()
                            + " units; Available capacity: "
                            + availableCapacity
                            + " units; " + requestedLabel
                            + request.getQuantity() + " units.");
        }

        if (newQuantity < 0) {
            throw new BusinessException(
                    "Insufficient stock. Available: "
                            + stock.getQuantity() + " units");
        }

        if (newQuantity > Integer.MAX_VALUE) {
            throw new BusinessException(
                    "Resulting stock quantity exceeds the supported range.");
        }

        if (newQuantity > previousQuantity) {
            validateWarehouseCapacity(
                    warehouse, stock, previousQuantity, (int) newQuantity);
        }

        stock.setQuantity((int) newQuantity);
        stockRepository.save(stock);

        StockMovement movement = StockMovement.builder()
                .stock(stock)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .previousQuantity(previousQuantity)
                .newQuantity((int) newQuantity)
                .referenceNumber(request.getReferenceNumber())
                .destinationWarehouse(
                        request.getMovementType()
                                == StockMovement.MovementType.INBOUND
                                ? warehouse : null)
                .sourceWarehouse(
                        request.getMovementType()
                                == StockMovement.MovementType.OUTBOUND
                                ? warehouse : null)
                .reason(request.getReason())
                .performedBy(performedBy)
                .build();

        StockMovement saved = movementRepository.save(movement);

        notificationService.onStockChanged(stock);

        return toMovementResponse(saved);
    }

    public Stock createStock(Stock stock) {
        return createStock(stock, null);
    }

    public Stock createStock(Stock stock, Long performedByUserId) {
        Product product = productRepository
                .findByIdForUpdate(stock.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "id", stock.getProduct().getId()));
        stock.setProduct(product);
        Warehouse warehouse = warehouseRepository
                .findByIdForUpdate(stock.getWarehouse().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", stock.getWarehouse().getId()));
        stockRepository.findByWarehouseIdAndProductIdForUpdate(
                        warehouse.getId(), stock.getProduct().getId())
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "This product already has a stock allocation "
                                    + "in the selected warehouse.");
                });
        if (stock.getQuantity() == null || stock.getQuantity() < 0
                || stock.getMinQuantity() == null
                || stock.getMinQuantity() < 0
                || stock.getMaxQuantity() == null
                || stock.getMaxQuantity() < 1
                || stock.getMinQuantity() > stock.getMaxQuantity()) {
            throw new BusinessException(
                    "Stock, minimum and maximum quantities are invalid.");
        }
        if (stock.getQuantity() > stock.getMaxQuantity()) {
            throw new BusinessException(
                    "Initial stock cannot exceed the product maximum.");
        }
        validateWarehouseCapacity(warehouse, null, 0, stock.getQuantity());
        Stock saved = stockRepository.save(stock);
        if (performedByUserId != null && stock.getQuantity() > 0) {
            User actor = userRepository.findById(performedByUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User", "id", performedByUserId));
            movementRepository.save(StockMovement.builder()
                    .stock(saved)
                    .movementType(StockMovement.MovementType.INBOUND)
                    .quantity(saved.getQuantity())
                    .previousQuantity(0)
                    .newQuantity(saved.getQuantity())
                    .reason("Initial stock allocation")
                    .destinationWarehouse(warehouse)
                    .performedBy(actor)
                    .build());
        }
        notificationService.onStockChanged(saved);
        return saved;
    }

    public List<StockMovementResponse> transfer(
            StockTransferRequest request, Long performedByUserId) {
        if (request.getSourceWarehouseId().equals(
                request.getDestinationWarehouseId())) {
            throw new BusinessException(
                    "Source and destination warehouses must be different.");
        }
        User performedBy = userRepository.findById(performedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", performedByUserId));
        ensureWarehouseAccess(performedBy, request.getSourceWarehouseId());
        ensureWarehouseAccess(performedBy, request.getDestinationWarehouseId());
        Product product = productRepository
                .findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "id", request.getProductId()));

        Long firstId = Math.min(request.getSourceWarehouseId(),
                request.getDestinationWarehouseId());
        Long secondId = Math.max(request.getSourceWarehouseId(),
                request.getDestinationWarehouseId());
        Warehouse firstWarehouse = warehouseRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", firstId));
        Warehouse secondWarehouse = warehouseRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", secondId));
        Warehouse sourceWarehouse = firstId.equals(
                request.getSourceWarehouseId())
                ? firstWarehouse : secondWarehouse;
        Warehouse destinationWarehouse = firstId.equals(
                request.getDestinationWarehouseId())
                ? firstWarehouse : secondWarehouse;

        Stock source = stockRepository
                .findByWarehouseIdAndProductIdForUpdate(
                        sourceWarehouse.getId(), request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock not found for this product in source warehouse"));
        Stock destination = stockRepository
                .findByWarehouseIdAndProductIdForUpdate(
                        destinationWarehouse.getId(), request.getProductId())
                .orElse(null);
        if (request.getQuantity() > source.getQuantity()) {
            throw new BusinessException(
                    "Insufficient stock. Available: "
                            + source.getQuantity() + " units");
        }

        int sourceBefore = source.getQuantity();
        int destinationBefore = destination == null ? 0
                : destination.getQuantity();
        int sourceAfter = sourceBefore - request.getQuantity();
        long destinationAfterLong = (long) destinationBefore
                + request.getQuantity();
        if (destinationAfterLong > Integer.MAX_VALUE) {
            throw new BusinessException(
                    "Resulting stock quantity exceeds the supported range.");
        }
        int destinationAfter = (int) destinationAfterLong;
        int destinationMax = destination == null
                ? source.getMaxQuantity() : destination.getMaxQuantity();
        if (destinationAfter > destinationMax) {
            throw new BusinessException(
                    "Transfer exceeds the destination product maximum. "
                            + "Current stock: " + destinationBefore
                            + " units; Maximum stock: " + destinationMax
                            + " units; Available capacity: "
                            + Math.max(0, destinationMax - destinationBefore)
                            + " units; Requested transfer quantity: "
                            + request.getQuantity() + " units.");
        }
        Stock capacityStock = destination == null
                ? Stock.builder()
                .warehouse(destinationWarehouse)
                .product(product)
                .quantity(0)
                .minQuantity(source.getMinQuantity())
                .maxQuantity(destinationMax)
                .build()
                : destination;
        validateWarehouseCapacity(destinationWarehouse, capacityStock,
                destinationBefore, destinationAfter);

        source.setQuantity(sourceAfter);
        if (destination == null) {
            destination = capacityStock;
        }
        destination.setQuantity(destinationAfter);
        stockRepository.save(source);
        stockRepository.save(destination);

        StockMovement sourceMovement = StockMovement.builder()
                .stock(source)
                .movementType(StockMovement.MovementType.TRANSFER_OUT)
                .quantity(request.getQuantity())
                .previousQuantity(sourceBefore)
                .newQuantity(sourceAfter)
                .referenceNumber(request.getReferenceNumber())
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .reason(request.getReason())
                .performedBy(performedBy)
                .build();
        StockMovement destinationMovement = StockMovement.builder()
                .stock(destination)
                .movementType(StockMovement.MovementType.TRANSFER_IN)
                .quantity(request.getQuantity())
                .previousQuantity(destinationBefore)
                .newQuantity(destinationAfter)
                .referenceNumber(request.getReferenceNumber())
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .reason(request.getReason())
                .performedBy(performedBy)
                .build();
        List<StockMovementResponse> responses = movementRepository.saveAll(
                        List.of(sourceMovement, destinationMovement))
                .stream().map(this::toMovementResponse).toList();
        notificationService.onStockChanged(source);
        notificationService.onStockChanged(destination);
        return responses;
    }

    private void validateWarehouseCapacity(
            Warehouse warehouse,
            Stock changedStock,
            int previousQuantity,
            int newQuantity) {
        BigDecimal usedCapacity = stockRepository
                .sumStorageCapacityByWarehouseId(warehouse.getId());
        if (usedCapacity == null) {
            usedCapacity = BigDecimal.ZERO;
        }
        BigDecimal factor = changedStock == null
                ? BigDecimal.ONE
                : changedStock.getProduct().getStorageCapacityFactor();
        BigDecimal projected = usedCapacity.add(factor.multiply(
                BigDecimal.valueOf((long) newQuantity - previousQuantity)));
        BigDecimal capacity = BigDecimal.valueOf(warehouse.getCapacity());
        if (projected.compareTo(capacity) > 0) {
            BigDecimal available = capacity.subtract(usedCapacity)
                    .max(BigDecimal.ZERO);
            throw new BusinessException(
                    "Warehouse capacity exceeded. Used: "
                            + usedCapacity.stripTrailingZeros().toPlainString()
                            + " storage units; Capacity: " + capacity
                            + " storage units; Available: "
                            + available.stripTrailingZeros().toPlainString()
                            + " storage units; Requested: "
                            + factor.multiply(BigDecimal.valueOf(
                                    (long) newQuantity - previousQuantity))
                            .stripTrailingZeros().toPlainString()
                            + " storage units.");
        }
    }

    private int calculateDelta(
            StockMovement.MovementType type,
            int quantity,
            StockMovementRequest.AdjustmentDirection adjustmentDirection) {

        return switch (type) {
            case INBOUND -> quantity;
            case OUTBOUND -> -quantity;
            case ADJUSTMENT -> adjustmentDirection
                    == StockMovementRequest.AdjustmentDirection.DECREASE
                    ? -quantity : quantity;
            case TRANSFER_IN -> quantity;
            case TRANSFER_OUT -> -quantity;
        };
    }

    private StockResponse toStockResponse(Stock stock) {
        Warehouse warehouse = stock.getWarehouse();
        Product product = stock.getProduct();

        return StockResponse.builder()
                .id(stock.getId())
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .quantity(stock.getQuantity())
                .minQuantity(stock.getMinQuantity())
                .maxQuantity(stock.getMaxQuantity())
                .version(stock.getVersion())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    private StockMovementResponse toMovementResponse(
            StockMovement movement) {

        Stock stock = movement.getStock();
        Warehouse warehouse = stock.getWarehouse();
        Product product = stock.getProduct();
        User user = movement.getPerformedBy();

        return StockMovementResponse.builder()
                .id(movement.getId())
                .stockId(stock.getId())
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .movementType(movement.getMovementType())
                .quantity(movement.getQuantity())
                .previousQuantity(movement.getPreviousQuantity())
                .newQuantity(movement.getNewQuantity())
                .referenceNumber(movement.getReferenceNumber())
                .sourceWarehouseId(movement.getSourceWarehouse() == null
                        ? null : movement.getSourceWarehouse().getId())
                .sourceWarehouseName(movement.getSourceWarehouse() == null
                        ? null : movement.getSourceWarehouse().getName())
                .destinationWarehouseId(
                        movement.getDestinationWarehouse() == null
                                ? null : movement.getDestinationWarehouse()
                                .getId())
                .destinationWarehouseName(
                        movement.getDestinationWarehouse() == null
                                ? null : movement.getDestinationWarehouse()
                                .getName())
                .reason(movement.getReason())
                .performedBy(user.getEmail())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }

    private void ensureWarehouseAccess(User user, Long warehouseId) {
        if (user.getRole() == User.Role.WAREHOUSE_MGR
                && (user.getWarehouse() == null
                || !user.getWarehouse().getId().equals(warehouseId))) {
            throw new AccessDeniedException(
                    "Warehouse managers can access only their assigned warehouse");
        }
    }
}
