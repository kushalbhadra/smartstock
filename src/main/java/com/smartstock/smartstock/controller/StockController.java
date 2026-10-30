
package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.dto.StockMovementResponse;
import com.smartstock.smartstock.dto.StockResponse;
import com.smartstock.smartstock.dto.StockTransferRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.service.ExcelService;
import com.smartstock.smartstock.service.StockService;
import com.smartstock.smartstock.service.SystemSettingsService;
import com.smartstock.smartstock.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    @Autowired
    private StockService stockService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private AuditEventService auditEventService;

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStocks(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                stockService.getAllStocks(userDetails.getUsername()));
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<StockResponse>> getByWarehouse(
            @PathVariable Long warehouseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                stockService.getStocksByWarehouse(
                        warehouseId, userDetails.getUsername()));
    }

    @GetMapping("/low")
    public ResponseEntity<List<StockResponse>> getLowStock(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                stockService.getLowStockItems(userDetails.getUsername()));
    }

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovementResponse>> getRecentMovements(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                stockService.getRecentMovements(userDetails.getUsername()));
    }

    @GetMapping("/movements/history")
    public ResponseEntity<Page<StockMovementResponse>> getMovementHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) StockMovement.MovementType type,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(stockService.getMovementHistory(
                userDetails.getUsername(), type, warehouseId, from, to,
                search, page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Stock> createStock(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long warehouseId =
                Long.valueOf(body.get("warehouseId").toString());
        Long productId =
                Long.valueOf(body.get("productId").toString());
        Integer quantity =
                Integer.valueOf(body.get("quantity").toString());
        Integer minQuantity = body.get("minQuantity") == null
                ? systemSettingsService.getDefaultLowStockThreshold()
                : Integer.valueOf(body.get("minQuantity").toString());
        Integer maxQuantity =
                Integer.valueOf(body.get("maxQuantity").toString());

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse", "id", warehouseId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product", "id", productId));

        Stock stock = Stock.builder()
                .warehouse(warehouse)
                .product(product)
                .quantity(quantity)
                .minQuantity(minQuantity)
                .maxQuantity(maxQuantity)
                .build();

        User actor = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
        Stock saved = stockService.createStock(stock, actor.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MGR')")
    public ResponseEntity<StockMovementResponse> recordMovement(
            @Valid @RequestBody StockMovementRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();

        StockMovementResponse response = stockService.recordMovement(
                request, user.getId());
        auditEventService.record(user.getEmail(), user.getName(),
                "RECORDED", "STOCK_MOVEMENT", response.getId(),
                request.getMovementType() + " movement of "
                        + request.getQuantity() + " units for "
                        + response.getProductName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockMovementResponse>> transfer(
            @Valid @RequestBody StockTransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
        List<StockMovementResponse> responses = stockService.transfer(
                request, user.getId());
        auditEventService.record(user.getEmail(), user.getName(),
                "TRANSFERRED", "STOCK", request.getProductId(),
                "Transferred " + request.getQuantity() + " units from "
                        + request.getSourceWarehouseId() + " to "
                        + request.getDestinationWarehouseId());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportStocks(
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        byte[] data = excelService.exportStocks();
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "EXPORTED", "STOCK_DATA",
                null, "Exported stock levels");

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=stock-levels.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet")
                .body(data);
    }

    @GetMapping("/movements/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MGR')")
    public ResponseEntity<byte[]> exportMovements(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) StockMovement.MovementType type,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search) throws Exception {
        byte[] data = excelService.exportMovements(
                stockService.getMovementsForExport(
                        userDetails.getUsername(), type, warehouseId,
                        from, to, search));
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "EXPORTED", "MOVEMENT_DATA",
                null, "Exported stock movements");

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=movements.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet")
                .body(data);
    }
}
