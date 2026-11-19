package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierProductRequest {

    @NotNull
    private Long productId;

    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    private BigDecimal purchasePrice;

    @PositiveOrZero
    private Integer leadTimeDays;

    @Size(max = 100)
    private String supplierProductCode;
}
