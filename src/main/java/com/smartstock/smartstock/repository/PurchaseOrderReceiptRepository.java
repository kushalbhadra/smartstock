package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.PurchaseOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderReceiptRepository
        extends JpaRepository<PurchaseOrderReceipt, Long> {

    boolean existsByPurchaseOrderIdAndReferenceNumberIgnoreCase(
            Long purchaseOrderId, String referenceNumber);

    List<PurchaseOrderReceipt> findByPurchaseOrderIdOrderByReceivedDateAsc(
            Long purchaseOrderId);
}
