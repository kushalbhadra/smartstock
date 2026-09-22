package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.ApiResponse;
import com.smartstock.smartstock.dto.ProductRequest;
import com.smartstock.smartstock.dto.ProductResponse;
import com.smartstock.smartstock.service.ExcelService;
import com.smartstock.smartstock.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ExcelService excelService;

    // GET /api/products — all authenticated users
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // GET /api/products/5 — all authenticated users
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // GET /api/products/search?keyword=laptop
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(
            @RequestParam String keyword) {
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }


    // GET /api/products/export
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProducts()
            throws Exception {
        byte[] data = excelService.exportProducts();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=products.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument" +
                                ".spreadsheetml.sheet")
                .body(data);
    }

    // GET /api/products/template
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate()
            throws Exception {
        byte[] data = excelService.generateProductTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=products-template.xlsx")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument" +
                                ".spreadsheetml.sheet")
                .body(data);
    }

    // POST /api/products — ADMIN only
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/products/5 — ADMIN only
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(
                productService.updateProduct(id, request));
    }

    // DELETE /api/products/5 — ADMIN only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> deleteProduct(
            @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(
                ApiResponse.success("Product deleted successfully"));
    }
    // POST /api/products/import
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importProducts(
            @RequestParam("file")
            org.springframework.web.multipart.MultipartFile file)
            throws Exception {

        byte[] bytes = file.getBytes();
        ExcelService.ImportResult result =
                excelService.importProducts(bytes);

        return ResponseEntity.ok(Map.of(
                "success", result.successCount(),
                "failed",  result.failedCount(),
                "errors",  result.errors()
        ));
    }
}