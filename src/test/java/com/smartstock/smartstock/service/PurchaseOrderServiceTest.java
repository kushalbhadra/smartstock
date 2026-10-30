package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.PurchaseOrderReceiptRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderReceiptRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderReceiptRepository receiptRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockService stockService;
    @Mock
    private NotificationService notificationService;

    @Test
    void rejectsDuplicateReceiptReferenceWithoutChangingInventory() {
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Main warehouse")
                .build();
        PurchaseOrder order = PurchaseOrder.builder()
                .id(6L)
                .poNumber("PO-6")
                .warehouse(warehouse)
                .quantity(10)
                .receivedQuantity(2)
                .status(PurchaseOrder.Status.PARTIALLY_RECEIVED)
                .build();
        User actor = User.builder()
                .id(5L)
                .email("admin@smartstock.com")
                .role(User.Role.ADMIN)
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(receiptRepository
                .existsByPurchaseOrderIdAndReferenceNumberIgnoreCase(
                        6L, "RCPT-1"))
                .thenReturn(true);

        PurchaseOrderReceiptRequest request =
                new PurchaseOrderReceiptRequest();
        request.setQuantity(3);
        request.setReceivedDate(LocalDate.now());
        request.setWarehouseId(2L);
        request.setReferenceNumber(" RCPT-1 ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> newService().receive(
                        6L, request, actor.getEmail()));

        assertEquals(
                "This receipt reference has already been processed.",
                exception.getMessage());
        assertEquals(2, order.getReceivedQuantity());
        verifyNoInteractions(stockService, notificationService);
        verify(receiptRepository, never()).save(
                org.mockito.ArgumentMatchers.any());
    }

    private PurchaseOrderService newService() {
        return new PurchaseOrderService(
                purchaseOrderRepository,
                receiptRepository,
                supplierRepository,
                productRepository,
                warehouseRepository,
                userRepository,
                stockService,
                notificationService);
    }
}
