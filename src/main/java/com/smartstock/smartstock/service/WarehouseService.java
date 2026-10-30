
package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.WarehouseResponse;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private NotificationService notificationService;

    // Get all warehouses
    // Caching is temporarily disabled until Redis serialization is fixed.
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses(String email) {
        User user = findUser(email);
        List<Warehouse> warehouses;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            warehouses = user.getWarehouse() == null
                    ? List.of()
                    : List.of(user.getWarehouse());
        } else {
            warehouses = warehouseRepository.findAll();
        }
        return warehouses
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Get warehouse by ID
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id, String email) {
        User user = findUser(email);
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Warehouse", "id", id));

        if (user.getRole() == User.Role.WAREHOUSE_MGR
                && (user.getWarehouse() == null
                || !user.getWarehouse().getId().equals(id))) {
            throw new AccessDeniedException(
                    "Warehouse managers can access only their assigned warehouse");
        }

        return toResponse(warehouse);
    }

    // Create new warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public WarehouseResponse createWarehouse(Warehouse warehouse) {
        validateWarehouse(warehouse);
        Warehouse saved = warehouseRepository.save(warehouse);
        return toResponse(saved);
    }

    // Update existing warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public WarehouseResponse updateWarehouse(Long id, Warehouse updated) {
        Warehouse existing = warehouseRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Warehouse", "id", id));

        validateWarehouse(updated);
        java.math.BigDecimal usedCapacity = stockRepository
                .sumStorageCapacityByWarehouseId(id);
        if (java.math.BigDecimal.valueOf(updated.getCapacity())
                .compareTo(usedCapacity) < 0) {
            throw new BusinessException(
                    "Warehouse capacity cannot be reduced below its current "
                            + "usage of "
                            + usedCapacity.stripTrailingZeros().toPlainString()
                            + " storage units.");
        }
        existing.setName(updated.getName());
        existing.setLocation(updated.getLocation());
        existing.setCapacity(updated.getCapacity());

        Warehouse saved = warehouseRepository.save(existing);
        notificationService.onWarehouseInventoryChanged(saved);
        return toResponse(saved);
    }

    // Delete warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public void deleteWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Warehouse", "id", id));
        if (stockRepository.existsByWarehouseId(id)) {
            throw new BusinessException(
                    "A warehouse with stock cannot be deleted. "
                            + "Transfer or remove its stock first.");
        }
        if (purchaseOrderRepository.existsByWarehouseIdAndStatusIn(
                id, List.of(PurchaseOrder.Status.PENDING,
                        PurchaseOrder.Status.APPROVED))) {
            throw new BusinessException(
                    "A warehouse with pending purchase orders cannot be deleted.");
        }

        warehouseRepository.delete(warehouse);
    }

    // Convert entity to DTO to avoid circular JSON relationships
    private WarehouseResponse toResponse(Warehouse warehouse) {
        java.math.BigDecimal usedCapacity = stockRepository
                .sumStorageCapacityByWarehouseId(warehouse.getId());
        java.math.BigDecimal capacity = java.math.BigDecimal.valueOf(
                warehouse.getCapacity());
        java.math.BigDecimal utilization = capacity.signum() == 0
                ? java.math.BigDecimal.ZERO
                : usedCapacity.multiply(java.math.BigDecimal.valueOf(100))
                .divide(capacity, 2, java.math.RoundingMode.HALF_UP);
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .location(warehouse.getLocation())
                .capacity(warehouse.getCapacity())
                .usedCapacity(usedCapacity)
                .remainingCapacity(capacity.subtract(usedCapacity))
                .utilizationPercentage(utilization)
                .productCount(stockRepository.countByWarehouseId(
                        warehouse.getId()))
                .capacityUnit("storage units")
                .createdAt(warehouse.getCreatedAt())
                .build();
    }

    private void validateWarehouse(Warehouse warehouse) {
        if (warehouse.getCapacity() == null || warehouse.getCapacity() < 1) {
            throw new BusinessException(
                    "Warehouse capacity must be at least 1 storage unit.");
        }
        if (warehouse.getName() == null || warehouse.getName().isBlank()
                || warehouse.getLocation() == null
                || warehouse.getLocation().isBlank()) {
            throw new BusinessException(
                    "Warehouse name and location are required.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }
}
