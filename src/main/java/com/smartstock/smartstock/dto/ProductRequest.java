package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

// What React sends when creating/updating a product
@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Name cannot exceed 200 characters")
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU cannot exceed 50 characters")
    private String sku;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Storage capacity factor must be greater than 0")
    @Digits(integer = 8, fraction = 4,
            message = "Storage capacity factor supports up to 4 decimal places")
    private BigDecimal storageCapacityFactor = BigDecimal.ONE;

    @Positive
    private Long initialWarehouseId;

    @Min(0)
    private Integer initialQuantity;

    @Min(0)
    private Integer minQuantity;

    @Min(1)
    private Integer maxQuantity;
}