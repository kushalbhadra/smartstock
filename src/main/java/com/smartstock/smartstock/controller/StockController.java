package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.service.ExcelService;
import com.smartstock.smartstock.service.StockService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    @Autowired private StockService stockService;
    @Autowired private UserRepository userRepository;

    @Autowired
    private ExcelService excelService;

    // GET /api/stocks
    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    // GET /api/stocks/warehouse/2
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<Stock>> getByWarehouse(
            @PathVariable Long warehouseId) {
        return ResponseEntity.ok(
                stockService.getStocksByWarehouse(warehouseId));
    }

    // GET /api/stocks/low
    @GetMapping("/low")
    public ResponseEntity<List<Stock>> getLowStock() {
        return ResponseEntity.ok(stockService.getLowStockItems());
    }

    // GET /api/stocks/movements
    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getRecentMovements() {
        return ResponseEntity.ok(stockService.getRecentMovements());
    }

    // GET /api/stocks/export
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportStocks()
            throws Exception {
        byte[] data = excelService.exportStocks();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=stock-levels.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument" +
                                ".spreadsheetml.sheet")
                .body(data);
    }

    // GET /api/stocks/movements/export
    @GetMapping("/movements/export")
    public ResponseEntity<byte[]> exportMovements()
            throws Exception {
        byte[] data = excelService.exportMovements();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=movements.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument" +
                                ".spreadsheetml.sheet")
                .body(data);
    }

    // POST /api/stocks/movements — ADMIN or WAREHOUSE_MGR
    @PostMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MGR')")
    public ResponseEntity<StockMovement> recordMovement(
            @Valid @RequestBody StockMovementRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Get current logged-in user ID
        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow();

        return ResponseEntity.ok(
                stockService.recordMovement(request, user.getId()));
    }
}