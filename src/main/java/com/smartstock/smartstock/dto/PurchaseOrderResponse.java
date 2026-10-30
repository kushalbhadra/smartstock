package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        SupplierSummary supplier,
        ProductSummary product,
        WarehouseSummary warehouse,
        Integer quantity,
        Integer receivedQuantity,
        Integer remainingQuantity,
        BigDecimal purchaseUnitPrice,
        BigDecimal total,
        PurchaseOrder.Status status,
        LocalDate expectedDate,
        UserSummary createdBy,
        LocalDateTime createdAt
) {
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                new SupplierSummary(
                        order.getSupplier().getId(),
                        order.getSupplier().getName(),
                        order.getSupplier().getContactEmail(),
                        order.getSupplier().getPhone(),
                        order.getSupplier().getRating()
                ),
                new ProductSummary(
                        order.getProduct().getId(),
                        order.getProduct().getName(),
                        order.getProduct().getSku()
                ),
                new WarehouseSummary(
                        order.getWarehouse().getId(),
                        order.getWarehouse().getName()
                ),
                order.getQuantity(),
                order.getReceivedQuantity(),
                order.getQuantity() - order.getReceivedQuantity(),
                order.getPurchaseUnitPrice(),
                order.getPurchaseUnitPrice().multiply(
                        BigDecimal.valueOf(order.getQuantity())),
                order.getStatus(),
                order.getExpectedDate(),
                new UserSummary(
                        order.getCreatedBy().getId(),
                        order.getCreatedBy().getName(),
                        order.getCreatedBy().getEmail()
                ),
                order.getCreatedAt()
        );
    }

    public record SupplierSummary(
            Long id,
            String name,
            String contactEmail,
            String phone,
            BigDecimal rating
    ) {
    }

    public record ProductSummary(Long id, String name, String sku) {
    }

    public record WarehouseSummary(Long id, String name) {
    }

    public record UserSummary(Long id, String name, String email) {
    }
}
