package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.entity.*;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StockService {

    @Autowired private StockRepository stockRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private WhatsAppAlertService whatsAppAlertService;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private UserRepository userRepository;

    // Get all stocks
    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    // Get stocks by warehouse
    @Transactional(readOnly = true)
    public List<Stock> getStocksByWarehouse(Long warehouseId) {
        return stockRepository.findByWarehouseId(warehouseId);
    }

    // Get low stock items
    @Transactional(readOnly = true)
    public List<Stock> getLowStockItems() {
        return stockRepository.findLowStockItems();
    }

    // Record stock movement — core business logic
    public StockMovement recordMovement(StockMovementRequest request,
                                        Long performedByUserId) {
        // Find the stock entry
        Stock stock = stockRepository
                .findByWarehouseIdAndProductId(
                        request.getWarehouseId(),
                        request.getProductId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock not found for this product in this warehouse"
                ));

        User performedBy = userRepository.findById(performedByUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "id", performedByUserId));

        // Calculate new quantity
        int delta = calculateDelta(
                request.getMovementType(),
                request.getQuantity()
        );
        int newQuantity = stock.getQuantity() + delta;

        // Business rule: quantity cannot go negative
        if (newQuantity < 0) {
            throw new BusinessException(
                    "Insufficient stock. Available: " +
                            stock.getQuantity() + " units"
            );
        }

        // Update stock quantity
        stock.setQuantity(newQuantity);
        stockRepository.save(stock);

        // Record the movement for audit trail
        StockMovement movement = StockMovement.builder()
                .stock(stock)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .performedBy(performedBy)
                .build();

        // Save the movement
        StockMovement saved = movementRepository.save(movement);

        // Check if stock is now low — trigger WhatsApp alert
        // This runs AFTER the transaction succeeds
        if (stock.getQuantity() < stock.getMinQuantity()) {
            try {
                whatsAppAlertService.sendLowStockAlert(stock);
            } catch (Exception e) {
                // IMPORTANT: never let alert failure break
                // the main stock operation
                // Alert failure is logged in alert_logs table
                System.err.println(
                        "WhatsApp alert failed: " + e.getMessage()
                );
            }
        }

        return saved;
    }

    // Calculate quantity change based on movement type
    private int calculateDelta(StockMovement.MovementType type,
                               int quantity) {
        return switch (type) {
            case INBOUND    ->  quantity;  // adds to stock
            case OUTBOUND   -> -quantity;  // removes from stock
            case ADJUSTMENT ->  quantity;  // can be + or -
        };
    }

    // Get recent movements
    @Transactional(readOnly = true)
    public List<StockMovement> getRecentMovements() {
        return movementRepository.findTop10ByOrderByCreatedAtDesc();
    }
}