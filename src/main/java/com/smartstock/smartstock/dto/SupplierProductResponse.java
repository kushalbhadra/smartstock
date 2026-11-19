package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.SupplierProduct;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SupplierProductResponse {

    Long productId;
    String productName;
    String sku;
    BigDecimal purchasePrice;
    Integer leadTimeDays;
    String supplierProductCode;
    boolean active;

    public static SupplierProductResponse from(SupplierProduct association) {
        return SupplierProductResponse.builder()
                .productId(association.getProduct().getId())
                .productName(association.getProduct().getName())
                .sku(association.getProduct().getSku())
                .purchasePrice(association.getPurchasePrice())
                .leadTimeDays(association.getLeadTimeDays())
                .supplierProductCode(
                        association.getSupplierProductCode())
                .active(Boolean.TRUE.equals(association.getActive()))
                .build();
    }
}
