package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.SupplierProduct;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SupplierProductAdminResponse {

    Long id;
    Long supplierId;
    String supplierName;
    Long productId;
    String productName;
    String sku;
    BigDecimal purchasePrice;
    Integer leadTimeDays;
    String supplierProductCode;
    boolean active;

    public static SupplierProductAdminResponse from(
            SupplierProduct association) {
        return SupplierProductAdminResponse.builder()
                .id(association.getId())
                .supplierId(association.getSupplier().getId())
                .supplierName(association.getSupplier().getName())
                .productId(association.getProduct().getId())
                .productName(association.getProduct().getName())
                .sku(association.getProduct().getSku())
                .purchasePrice(association.getPurchasePrice())
                .leadTimeDays(association.getLeadTimeDays())
                .supplierProductCode(association.getSupplierProductCode())
                .active(Boolean.TRUE.equals(association.getActive()))
                .build();
    }
}
