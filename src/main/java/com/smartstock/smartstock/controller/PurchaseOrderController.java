package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.PurchaseOrderRequest;
import com.smartstock.smartstock.dto.PurchaseOrderResponse;
import com.smartstock.smartstock.dto.PurchaseOrderStatusRequest;
import com.smartstock.smartstock.dto.PurchaseOrderReceiptRequest;
import com.smartstock.smartstock.service.PurchaseOrderService;
import com.smartstock.smartstock.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final AuditEventService auditEventService;

    public PurchaseOrderController(
            PurchaseOrderService purchaseOrderService,
            AuditEventService auditEventService) {
        this.purchaseOrderService = purchaseOrderService;
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(
                purchaseOrderService.getAll(userDetails.getUsername()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MGR')")
    public ResponseEntity<PurchaseOrderResponse> create(
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PurchaseOrderResponse response = purchaseOrderService.create(
                request, userDetails.getUsername());
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "CREATED", "PURCHASE_ORDER",
                response.id(), "Created purchase order "
                        + response.poNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PurchaseOrderResponse response = purchaseOrderService.updateStatus(
                id, request.getStatus());
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "UPDATED_STATUS",
                "PURCHASE_ORDER", response.id(),
                "Changed purchase order " + response.poNumber()
                        + " to " + response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MGR')")
    public ResponseEntity<PurchaseOrderResponse> receive(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderReceiptRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PurchaseOrderResponse response = purchaseOrderService.receive(
                id, request, userDetails.getUsername());
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "RECEIVED", "PURCHASE_ORDER",
                id, "Received " + request.getQuantity()
                        + " units against " + response.poNumber());
        return ResponseEntity.ok(response);
    }
}
