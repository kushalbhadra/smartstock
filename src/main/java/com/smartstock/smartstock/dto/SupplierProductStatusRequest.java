package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SupplierProductStatusRequest {

    @NotNull
    private Boolean active;
}
