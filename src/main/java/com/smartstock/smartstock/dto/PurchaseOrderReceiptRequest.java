package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PurchaseOrderReceiptRequest {

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private LocalDate receivedDate;

    @NotNull
    @Positive
    private Long warehouseId;

    @NotBlank
    @Size(max = 120)
    private String referenceNumber;

    @Size(max = 500)
    private String notes;
}
