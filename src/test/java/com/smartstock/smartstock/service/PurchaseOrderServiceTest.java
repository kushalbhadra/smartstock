package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.PurchaseOrderReceiptRequest;
import com.smartstock.smartstock.dto.PurchaseOrderRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.SupplierProduct;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderReceiptRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.SupplierProductRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderReceiptRepository receiptRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private SupplierProductRepository supplierProductRepository;
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

    @Test
    void rejectsReceivingCancelledPurchaseOrder() {
        assertReceivingRejected(PurchaseOrder.Status.CANCELLED);
    }

    @Test
    void receivesPartialQuantityAndUpdatesStockOnlyForReceipt() {
        PurchaseOrder order = order(PurchaseOrder.Status.APPROVED);
        order.setReceivedQuantity(2);
        User actor = admin();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(receiptRepository
                .existsByPurchaseOrderIdAndReferenceNumberIgnoreCase(
                        6L, "RCPT-4"))
                .thenReturn(false);
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        PurchaseOrderReceiptRequest request = new PurchaseOrderReceiptRequest();
        request.setQuantity(3);
        request.setReceivedDate(LocalDate.now());
        request.setWarehouseId(2L);
        request.setReferenceNumber("RCPT-4");

        var response = newService().receive(6L, request, actor.getEmail());

        assertEquals(5, response.receivedQuantity());
        assertEquals(PurchaseOrder.Status.PARTIALLY_RECEIVED,
                response.status());
        verify(stockService).recordPurchaseOrderReceipt(
                2L, 3L, 3, 10, "RCPT-4",
                "Purchase order PO-6 receipt", actor.getId());
        verify(receiptRepository).save(any());
    }

    @Test
    void rejectsPurchaseOrderCreationForUnmappedProduct() {
        User actor = admin();
        Warehouse warehouse = Warehouse.builder().id(2L).build();
        Supplier supplier = Supplier.builder()
                .id(4L).name("Supplier").active(true).build();
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(warehouseRepository.findById(2L))
                .thenReturn(Optional.of(warehouse));
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> newService().create(orderRequest(), actor.getEmail()));

        verify(purchaseOrderRepository, never()).save(any());
        verifyNoInteractions(stockService);
    }

    @Test
    void rejectsInactiveSupplierProductMapping() {
        User actor = admin();
        Warehouse warehouse = Warehouse.builder().id(2L).build();
        Supplier supplier = Supplier.builder()
                .id(4L).name("Supplier").active(true).build();
        SupplierProduct mapping = SupplierProduct.builder()
                .id(8L)
                .supplier(supplier)
                .product(Product.builder().id(3L).name("Widget").build())
                .active(false)
                .build();
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(warehouseRepository.findById(2L))
                .thenReturn(Optional.of(warehouse));
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.of(mapping));

        assertThrows(BusinessException.class,
                () -> newService().create(orderRequest(), actor.getEmail()));

        verify(purchaseOrderRepository, never()).save(any());
        verifyNoInteractions(stockService);
    }

    @Test
    void managerCanCreateMappedPurchaseOrderOnlyForAssignedWarehouse() {
        Warehouse warehouse = Warehouse.builder()
                .id(2L).name("Main warehouse").build();
        User manager = User.builder()
                .id(7L)
                .name("Manager")
                .email("manager@smartstock.com")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(warehouse)
                .build();
        Supplier supplier = Supplier.builder()
                .id(4L).name("Supplier").active(true).build();
        Product product = Product.builder()
                .id(3L).name("Widget").sku("W-1").build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.of(SupplierProduct.builder()
                        .id(8L)
                        .supplier(supplier)
                        .product(product)
                        .active(true)
                        .build()));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> {
                    PurchaseOrder saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(13L);
                    return saved;
                });

        var response = newService().create(
                orderRequest(), manager.getEmail());

        assertEquals(13L, response.id());
        assertEquals(2L, response.warehouse().id());
        assertEquals(7L, response.createdBy().id());
        verifyNoInteractions(warehouseRepository);
        verifyNoInteractions(stockService);
    }

    @Test
    void createsMappedPurchaseOrderWithoutIncreasingStock() {
        User actor = admin();
        Warehouse warehouse = Warehouse.builder()
                .id(2L).name("Main warehouse").build();
        Supplier supplier = Supplier.builder()
                .id(4L).name("Supplier").active(true).build();
        Product product = Product.builder()
                .id(3L).name("Widget").sku("W-1").build();
        SupplierProduct mapping = SupplierProduct.builder()
                .id(8L)
                .supplier(supplier)
                .product(product)
                .active(true)
                .build();
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(warehouseRepository.findById(2L))
                .thenReturn(Optional.of(warehouse));
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.of(mapping));
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(product));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> {
                    PurchaseOrder saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(12L);
                    return saved;
                });

        var response = newService().create(
                orderRequest(), actor.getEmail());

        assertEquals(12L, response.id());
        assertEquals(PurchaseOrder.Status.PENDING, response.status());
        verify(purchaseOrderRepository, org.mockito.Mockito.times(2))
                .save(any(PurchaseOrder.class));
        verifyNoInteractions(stockService);
    }

    @Test
    void viewerCannotCreatePurchaseOrdersThroughService() {
        User viewer = User.builder()
                .email("viewer@smartstock.com")
                .role(User.Role.VIEWER)
                .build();
        when(userRepository.findByEmail(viewer.getEmail()))
                .thenReturn(Optional.of(viewer));

        assertThrows(AccessDeniedException.class,
                () -> newService().create(orderRequest(), viewer.getEmail()));

        verifyNoInteractions(warehouseRepository, supplierRepository,
                supplierProductRepository, productRepository,
                purchaseOrderRepository, stockService);
    }

    @Test
    void managerCannotCreatePurchaseOrderForAnotherWarehouse() {
        User manager = User.builder()
                .id(7L)
                .email("manager@smartstock.com")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        PurchaseOrderRequest request = orderRequest();
        request.setWarehouseId(9L);

        assertThrows(AccessDeniedException.class,
                () -> newService().create(request, manager.getEmail()));

        verifyNoInteractions(warehouseRepository);
        verifyNoInteractions(supplierRepository, supplierProductRepository,
                productRepository, purchaseOrderRepository, stockService);
    }

    @Test
    void rejectsReceivingFullyReceivedPurchaseOrder() {
        assertReceivingRejected(PurchaseOrder.Status.RECEIVED);
    }

    @Test
    void rejectsReceiptExceedingRemainingQuantity() {
        PurchaseOrder order = order(PurchaseOrder.Status.PARTIALLY_RECEIVED);
        order.setReceivedQuantity(8);
        User actor = User.builder()
                .id(5L)
                .email("admin@smartstock.com")
                .role(User.Role.ADMIN)
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));

        PurchaseOrderReceiptRequest request =
                new PurchaseOrderReceiptRequest();
        request.setQuantity(3);
        request.setReceivedDate(LocalDate.now());
        request.setWarehouseId(2L);
        request.setReferenceNumber("RCPT-3");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> newService().receive(
                        6L, request, actor.getEmail()));

        assertEquals(
                "Received quantity exceeds the remaining order quantity. "
                        + "Remaining: 2; requested: 3.",
                exception.getMessage());
        verifyNoInteractions(stockService, notificationService);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void allowsPendingPurchaseOrderToBeApproved() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        var response = newService().updateStatus(
                6L, PurchaseOrder.Status.APPROVED, admin().getEmail());

        assertEquals(PurchaseOrder.Status.APPROVED, response.status());
        verify(notificationService).onPurchaseOrderStatus(order);
        verifyNoInteractions(stockService);
    }

    @Test
    void allowsPendingPurchaseOrderToBeCancelled() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        var response = newService().updateStatus(
                6L, PurchaseOrder.Status.CANCELLED, admin().getEmail());

        assertEquals(PurchaseOrder.Status.CANCELLED, response.status());
    }

    @Test
    void rejectsCancellingApprovedPurchaseOrder() {
        PurchaseOrder order = order(PurchaseOrder.Status.APPROVED);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();

        assertThrows(BusinessException.class, () -> newService().updateStatus(
                6L, PurchaseOrder.Status.CANCELLED, admin().getEmail()));

        assertEquals(PurchaseOrder.Status.APPROVED, order.getStatus());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsApprovingCancelledPurchaseOrder() {
        PurchaseOrder order = order(PurchaseOrder.Status.CANCELLED);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();

        assertThrows(BusinessException.class, () -> newService().updateStatus(
                6L, PurchaseOrder.Status.APPROVED, admin().getEmail()));

        assertEquals(PurchaseOrder.Status.CANCELLED, order.getStatus());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void allowsConfiguredManagerToApproveAnotherManagersPendingOrder() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        User manager = User.builder()
                .id(6L)
                .email("manager@smartstock.com")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(order.getWarehouse())
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        var response = newService(true).updateStatus(
                6L, PurchaseOrder.Status.APPROVED, manager.getEmail());

        assertEquals(PurchaseOrder.Status.APPROVED, response.status());
        verify(notificationService).onPurchaseOrderStatus(order);
    }

    @Test
    void preventsManagerFromApprovingOwnPurchaseOrder() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        User manager = User.builder()
                .id(5L)
                .email("manager@smartstock.com")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(order.getWarehouse())
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));

        assertThrows(AccessDeniedException.class, () -> newService(true)
                .updateStatus(6L, PurchaseOrder.Status.APPROVED,
                        manager.getEmail()));

        verify(purchaseOrderRepository, never())
                .save(any(PurchaseOrder.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void managerApprovalIsDisabledByDefault() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        User manager = User.builder()
                .id(6L)
                .email("manager@smartstock.com")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(order.getWarehouse())
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));

        assertThrows(AccessDeniedException.class, () -> newService(false)
                .updateStatus(6L, PurchaseOrder.Status.APPROVED,
                        manager.getEmail()));
    }

    @Test
    void rejectsDirectApprovalToReceivedTransition() {
        PurchaseOrder order = order(PurchaseOrder.Status.APPROVED);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();

        assertThrows(BusinessException.class, () -> newService().updateStatus(
                6L, PurchaseOrder.Status.RECEIVED, admin().getEmail()));

        assertEquals(PurchaseOrder.Status.APPROVED, order.getStatus());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void repeatedApprovalIsIdempotent() {
        PurchaseOrder order = order(PurchaseOrder.Status.APPROVED);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        stubAdmin();

        var response = newService().updateStatus(
                6L, PurchaseOrder.Status.APPROVED, admin().getEmail());

        assertEquals(PurchaseOrder.Status.APPROVED, response.status());
        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void propagatesNotificationFailureDuringApproval() {
        PurchaseOrder order = order(PurchaseOrder.Status.PENDING);
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(order)).thenReturn(order);
        stubAdmin();
        doThrow(new IllegalStateException("Notification insert failed"))
                .when(notificationService).onPurchaseOrderStatus(order);

        assertThrows(IllegalStateException.class, () -> newService()
                .updateStatus(6L, PurchaseOrder.Status.APPROVED,
                        admin().getEmail()));
    }

    private PurchaseOrder order(PurchaseOrder.Status status) {
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Main warehouse")
                .build();
        return PurchaseOrder.builder()
                .id(6L)
                .poNumber("PO-6")
                .supplier(Supplier.builder()
                        .id(4L)
                        .name("Supplier")
                        .build())
                .product(Product.builder()
                        .id(3L)
                        .name("Widget")
                        .sku("W-1")
                        .build())
                .warehouse(warehouse)
                .quantity(10)
                .receivedQuantity(0)
                .purchaseUnitPrice(BigDecimal.TEN)
                .status(status)
                .createdBy(User.builder()
                        .id(5L)
                        .name("Admin")
                        .email("admin@smartstock.com")
                        .build())
                .build();
    }

    private void assertReceivingRejected(PurchaseOrder.Status status) {
        PurchaseOrder order = order(status);
        User actor = User.builder()
                .id(5L)
                .email("admin@smartstock.com")
                .role(User.Role.ADMIN)
                .build();
        when(purchaseOrderRepository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(order));
        when(userRepository.findByEmail(actor.getEmail()))
                .thenReturn(Optional.of(actor));

        PurchaseOrderReceiptRequest request =
                new PurchaseOrderReceiptRequest();
        request.setQuantity(1);
        request.setReceivedDate(LocalDate.now());
        request.setWarehouseId(2L);
        request.setReferenceNumber("RCPT-2");

        assertThrows(BusinessException.class, () -> newService().receive(
                6L, request, actor.getEmail()));

        verifyNoInteractions(stockService, notificationService);
        verify(receiptRepository, never()).save(any());
    }

    private PurchaseOrderRequest orderRequest() {
        PurchaseOrderRequest request = new PurchaseOrderRequest();
        request.setSupplierId(4L);
        request.setProductId(3L);
        request.setWarehouseId(2L);
        request.setQuantity(10);
        request.setPurchaseUnitPrice(BigDecimal.TEN);
        return request;
    }

    private User admin() {
        return User.builder()
                .id(5L)
                .email("admin@smartstock.com")
                .role(User.Role.ADMIN)
                .build();
    }

    private void stubAdmin() {
        when(userRepository.findByEmail(admin().getEmail()))
                .thenReturn(Optional.of(admin()));
    }

    private PurchaseOrderService newService() {
        return newService(false);
    }

    private PurchaseOrderService newService(
            boolean managerApprovalEnabled) {
        return new PurchaseOrderService(
                purchaseOrderRepository,
                receiptRepository,
                supplierRepository,
                supplierProductRepository,
                productRepository,
                warehouseRepository,
                userRepository,
                stockService,
                notificationService,
                managerApprovalEnabled);
    }
}
