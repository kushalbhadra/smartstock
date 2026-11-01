package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ProductRequest;
import com.smartstock.smartstock.dto.ProductResponse;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
// @Transactional means: wrap everything in a DB transaction
// If anything fails, roll back all changes
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private DemandForecastRepository demandForecastRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private NotificationService notificationService;

    // Get all products
  // @Cacheable(value = "products", key = "'all'")
    @Transactional(readOnly = true) // readOnly = faster for queries
    public List<ProductResponse> getAllProducts() {
        return toResponses(productRepository.findAll());
    }

    // Get single product by ID
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product", "id", id));
        return toResponse(product);
    }

    // Create new product
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        return createProduct(request, null);
    }

    public ProductResponse createProduct(
            ProductRequest request, String createdByEmail) {
        String normalizedSku = request.getSku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new BusinessException(
                    "Product with SKU " + normalizedSku + " already exists");
        }
        Product saved = productRepository.save(Product.builder()
                .name(request.getName().trim())
                .sku(normalizedSku)
                .category(request.getCategory().trim())
                .unit(request.getUnit().trim())
                .price(request.getPrice())
                .storageCapacityFactor(request.getStorageCapacityFactor())
                .build());
        Long createdByUserId = createdByEmail == null ? null
                : userRepository.findByEmail(createdByEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", createdByEmail))
                .getId();
        configureInitialAllocation(saved, request, createdByUserId);
        return toResponse(saved);
    }

    // Update existing product
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product", "id", id));

        // Check SKU uniqueness only if SKU changed
        String normalizedSku = request.getSku().trim().toUpperCase();
        if (!product.getSku().equals(normalizedSku)
                && productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new BusinessException(
                    "Product with SKU " + request.getSku() + " already exists"
            );
        }

        List<Warehouse> affectedWarehouses = validateStorageFactorChange(
                product, request);

        product.setName(request.getName().trim());
        product.setSku(normalizedSku);
        product.setCategory(request.getCategory().trim());
        product.setUnit(request.getUnit().trim());
        product.setPrice(request.getPrice());
        product.setStorageCapacityFactor(request.getStorageCapacityFactor());

        Product saved = productRepository.save(product);
        affectedWarehouses.forEach(
                notificationService::onWarehouseInventoryChanged);
        return toResponse(saved);
    }

    private List<Warehouse> validateStorageFactorChange(
            Product product,
            ProductRequest request) {
        java.math.BigDecimal newFactor = request.getStorageCapacityFactor();
        if (newFactor == null || newFactor.signum() <= 0) {
            throw new BusinessException(
                    "Storage capacity factor must be greater than zero.");
        }
        java.math.BigDecimal oldFactor = product.getStorageCapacityFactor();
        if (oldFactor.compareTo(newFactor) == 0) {
            return List.of();
        }

        List<Stock> allocations = stockRepository.findByProductId(product.getId());
        Map<Long, Long> quantityByWarehouse = allocations.stream()
                .collect(Collectors.groupingBy(
                        stock -> stock.getWarehouse().getId(),
                        Collectors.summingLong(Stock::getQuantity)));
        List<Long> warehouseIds = quantityByWarehouse.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Warehouse> affectedWarehouses = new java.util.ArrayList<>();

        for (Long warehouseId : warehouseIds) {
            Warehouse warehouse = warehouseRepository
                    .findByIdForUpdate(warehouseId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Warehouse", "id", warehouseId));
            java.math.BigDecimal usedCapacity = stockRepository
                    .sumStorageCapacityByWarehouseId(warehouseId);
            java.math.BigDecimal projectedCapacity = usedCapacity.add(
                    newFactor.subtract(oldFactor).multiply(
                            java.math.BigDecimal.valueOf(
                                    quantityByWarehouse.get(warehouseId))));
            if (projectedCapacity.compareTo(
                    java.math.BigDecimal.valueOf(warehouse.getCapacity())) > 0
                    && projectedCapacity.compareTo(usedCapacity) > 0) {
                throw new BusinessException(
                        "Storage capacity factor cannot be increased because "
                                + "it would exceed capacity in warehouse "
                                + warehouse.getName() + ".");
            }
            affectedWarehouses.add(warehouse);
        }
        return affectedWarehouses;
    }

    // Delete product
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        String deletionBlockedReason = deletionBlockedReason(
                stockRepository.existsByProductId(id),
                purchaseOrderRepository.existsByProductId(id),
                demandForecastRepository.existsByProductId(id),
                stockMovementRepository
                        .existsByProductIdInMovementHistory(id));
        if (deletionBlockedReason != null) {
            throw new BusinessException(deletionBlockedReason);
        }

        productRepository.deleteById(id);
    }

    // Search products
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String keyword) {
        return toResponses(productRepository.findByNameContainingIgnoreCase(
                keyword));
    }

    // Entity to DTO conversion — private helper method
    private ProductResponse toResponse(Product product) {
        String blockedReason = deletionBlockedReason(
                stockRepository.existsByProductId(product.getId()),
                purchaseOrderRepository.existsByProductId(product.getId()),
                demandForecastRepository.existsByProductId(product.getId()),
                stockMovementRepository
                        .existsByProductIdInMovementHistory(product.getId()));
        return toResponse(product, blockedReason);
    }

    private List<ProductResponse> toResponses(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();
        Set<Long> productIdsWithStock = new HashSet<>(
                stockRepository.findProductIdsWithStock(productIds));
        Set<Long> productIdsWithPurchaseOrders = new HashSet<>(
                purchaseOrderRepository.findProductIdsWithPurchaseOrders(
                        productIds));
        Set<Long> productIdsWithForecasts = new HashSet<>(
                demandForecastRepository.findProductIdsWithForecasts(
                        productIds));
        Set<Long> productIdsWithMovements = new HashSet<>(
                stockMovementRepository
                        .findProductIdsWithMovementHistory(productIds));

        return products.stream()
                .map(product -> {
                    List<String> references = new ArrayList<>();
                    Long id = product.getId();
                    if (productIdsWithStock.contains(id)) {
                        references.add("stock records");
                    }
                    if (productIdsWithPurchaseOrders.contains(id)) {
                        references.add("purchase orders");
                    }
                    if (productIdsWithForecasts.contains(id)) {
                        references.add("demand forecasts");
                    }
                    if (productIdsWithMovements.contains(id)) {
                        references.add("movement history");
                    }
                    return toResponse(product,
                            deletionBlockedReason(references));
                })
                .collect(Collectors.toList());
    }

    private ProductResponse toResponse(
            Product product, String deletionBlockedReason) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .category(product.getCategory())
                .unit(product.getUnit())
                .price(product.getPrice())
                .storageCapacityFactor(product.getStorageCapacityFactor())
                .createdAt(product.getCreatedAt())
                .canDelete(deletionBlockedReason == null)
                .deletionBlockedReason(deletionBlockedReason)
                .build();
    }

    private String deletionBlockedReason(
            boolean hasStock,
            boolean hasPurchaseOrders,
            boolean hasForecasts,
            boolean hasMovementHistory) {
        List<String> references = new ArrayList<>();
        if (hasStock) {
            references.add("stock records");
        }
        if (hasPurchaseOrders) {
            references.add("purchase orders");
        }
        if (hasForecasts) {
            references.add("demand forecasts");
        }
        if (hasMovementHistory) {
            references.add("movement history");
        }
        return deletionBlockedReason(references);
    }

    private String deletionBlockedReason(List<String> references) {
        return references.isEmpty()
                ? null
                : "Cannot delete product because it has "
                        + String.join(", ", references) + ".";
    }

    private void configureInitialAllocation(
            Product product,
            ProductRequest request,
            Long createdByUserId) {
        boolean hasAllocation = request.getInitialWarehouseId() != null
                || request.getInitialQuantity() != null
                || request.getMinQuantity() != null
                || request.getMaxQuantity() != null;
        if (!hasAllocation) {
            return;
        }
        if (request.getInitialWarehouseId() == null
                || request.getInitialQuantity() == null
                || request.getMaxQuantity() == null) {
            throw new BusinessException(
                    "Initial allocation requires a warehouse, quantity, "
                            + "and maximum quantity.");
        }
        Warehouse warehouse = warehouseRepository
                .findById(request.getInitialWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", request.getInitialWarehouseId()));
        int minQuantity = request.getMinQuantity() == null
                ? 0 : request.getMinQuantity();
        Stock stock = Stock.builder()
                .warehouse(warehouse)
                .product(product)
                .quantity(request.getInitialQuantity())
                .minQuantity(minQuantity)
                .maxQuantity(request.getMaxQuantity())
                .build();
        if (createdByUserId == null) {
            throw new BusinessException(
                    "Authenticated user is required for initial stock allocation.");
        }
        stockService.createStock(stock, createdByUserId);
    }
}