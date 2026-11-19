package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.SupplierProductAdminResponse;
import com.smartstock.smartstock.service.SupplierService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-products")
public class SupplierProductController {

    private final SupplierService supplierService;

    public SupplierProductController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupplierProductAdminResponse>> getAll() {
        return ResponseEntity.ok(
                supplierService.getAllSupplierProductMappings());
    }
}
