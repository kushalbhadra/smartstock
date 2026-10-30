package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.PurchaseOrder;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderStatusRequest {

    @NotNull
    private PurchaseOrder.Status status;
}
