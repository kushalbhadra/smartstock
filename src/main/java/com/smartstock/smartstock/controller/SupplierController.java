package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.ApiResponse;
import com.smartstock.smartstock.dto.SupplierRequest;
import com.smartstock.smartstock.dto.SupplierProductRequest;
import com.smartstock.smartstock.dto.SupplierProductResponse;
import com.smartstock.smartstock.dto.SupplierProductStatusRequest;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @GetMapping
    public ResponseEntity<List<Supplier>> getAll() {
        return ResponseEntity.ok(supplierService.getAllSuppliers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<List<SupplierProductResponse>> getProducts(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(supplierService.getSupplierProducts(
                id, userDetails.getUsername()));
    }

    @GetMapping("/{id}/products/active")
    public ResponseEntity<List<SupplierProductResponse>> getActiveProducts(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(supplierService.getActiveSupplierProducts(
                id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupplierProductResponse> addProduct(
            @PathVariable Long id,
            @Valid @RequestBody SupplierProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.addSupplierProduct(id, request));
    }

    @PatchMapping("/{id}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupplierProductResponse> updateProductStatus(
            @PathVariable Long id,
            @PathVariable Long productId,
            @Valid @RequestBody SupplierProductStatusRequest request) {
        return ResponseEntity.ok(supplierService.setSupplierProductActive(
                id, productId, request.getActive()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Supplier> create(
            @Valid @RequestBody SupplierRequest supplier) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.createSupplier(supplier));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Supplier> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest supplier) {
        return ResponseEntity.ok(
                supplierService.updateSupplier(id, supplier));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok(
                ApiResponse.success("Supplier deleted successfully"));
    }
}