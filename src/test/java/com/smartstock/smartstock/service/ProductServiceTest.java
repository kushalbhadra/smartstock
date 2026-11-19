package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ProductRequest;
import com.smartstock.smartstock.dto.ProductResponse;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.Notification;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.AuditEventRepository;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.SupplierProductRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private DemandForecastRepository demandForecastRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private SupplierProductRepository supplierProductRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private StockService stockService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductService productService;

    @Test
    void managerCannotSelectProductsFromAnotherWarehouse() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));

        assertThrows(AccessDeniedException.class,
                () -> productService.getStockedProductsByWarehouse(
                        9L, manager.getEmail()));

        verify(productRepository, never()).findStockedByWarehouseId(9L);
    }

    @Test
    void warehouseProductChoicesAreRestrictedToStockedProducts() {
        Warehouse warehouse = Warehouse.builder().id(2L).build();
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(warehouse)
                .build();
        Product stockedProduct = Product.builder()
                .id(3L)
                .name("Widget")
                .sku("W-1")
                .category("Other")
                .unit("piece")
                .price(BigDecimal.TEN)
                .storageCapacityFactor(BigDecimal.ONE)
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(warehouseRepository.findById(2L))
                .thenReturn(Optional.of(warehouse));
        when(productRepository.findStockedByWarehouseId(2L))
                .thenReturn(List.of(stockedProduct));
        when(stockRepository.findProductIdsWithStock(List.of(3L)))
                .thenReturn(List.of(3L));
        when(purchaseOrderRepository
                .findProductIdsWithPurchaseOrders(List.of(3L)))
                .thenReturn(List.of());
        when(demandForecastRepository.findProductIdsWithForecasts(List.of(3L)))
                .thenReturn(List.of());
        when(stockMovementRepository.findProductIdsWithMovementHistory(
                List.of(3L))).thenReturn(List.of());

        List<ProductResponse> result =
                productService.getStockedProductsByWarehouse(
                        2L, manager.getEmail());

        assertEquals(List.of(3L), result.stream()
                .map(ProductResponse::getId).toList());
        verify(productRepository).findStockedByWarehouseId(2L);
    }

    @Test
    void managerProductListUsesOnlyTheirAssignedWarehouse() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(productRepository.findStockedByWarehouseId(2L))
                .thenReturn(List.of());

        assertEquals(List.of(),
                productService.getAllProducts(manager.getEmail()));

        verify(productRepository).findStockedByWarehouseId(2L);
        verify(productRepository, never()).findAll();
    }

    @Test
    void managerCannotReadProductNotStockedInTheirWarehouse() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(Product.builder().id(3L).build()));
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(stockRepository.findByWarehouseIdAndProductId(2L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> productService.getProductById(
                        3L, manager.getEmail()));
    }

    @Test
    void managerSearchIsRestrictedToTheirWarehouse() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(productRepository.searchStockedByWarehouseId(2L, "widget"))
                .thenReturn(List.of());

        assertEquals(List.of(), productService.searchProducts(
                "widget", manager.getEmail()));
        verify(productRepository)
                .searchStockedByWarehouseId(2L, "widget");
        verify(productRepository, never())
                .findByNameContainingIgnoreCase("widget");
    }

    @Test
    void rejectsDeletingProductReferencedByDemandForecast() {
        Product product = deletableProduct();
        when(demandForecastRepository.existsByProductId(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has demand forecasts.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedByStock() {
        Product product = deletableProduct();
        when(stockRepository.existsByProductId(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has stock records.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedByPurchaseOrder() {
        Product product = deletableProduct();
        when(purchaseOrderRepository.existsByProductId(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has purchase orders.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedByMovementHistory() {
        Product product = deletableProduct();
        when(stockMovementRepository
                .existsByProductIdInMovementHistory(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has movement history.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedBySupplierMapping() {
        Product product = deletableProduct();
        when(supplierProductRepository.existsByProduct_Id(3L))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has "
                        + "supplier-product mappings.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedByNotification() {
        Product product = deletableProduct();
        when(notificationRepository.existsByTargetTypeAndTargetId(
                Notification.TargetType.PRODUCT, 3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has product notifications.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void rejectsDeletingProductReferencedByAuditHistory() {
        Product product = deletableProduct();
        when(auditEventRepository.existsProductHistory(3L)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it has product audit history.",
                exception.getMessage());
        verify(productRepository, never()).delete(product);
    }

    @Test
    void deletesProductWhenNoRecordsReferenceIt() {
        Product product = deletableProduct();

        productService.deleteProduct(3L);

        verify(productRepository).delete(product);
        verify(productRepository).flush();
    }

    @Test
    void reportsForeignKeyRaceAsADeletionConflict() {
        deletableProduct();
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(productRepository).flush();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.deleteProduct(3L));

        assertEquals(
                "Cannot delete product because it is still referenced by "
                        + "business records. Refresh the product list and "
                        + "review its related records.",
                exception.getMessage());
        verify(productRepository).delete(any(Product.class));
    }

    @Test
    void calculatesDeletionEligibilityForProductListsInBatches() {
        Product product = Product.builder()
                .id(3L)
                .name("Widget")
                .sku("W-1")
                .category("Other")
                .unit("piece")
                .price(BigDecimal.TEN)
                .storageCapacityFactor(BigDecimal.ONE)
                .build();
        User admin = User.builder()
                .email("admin@example.invalid")
                .role(User.Role.ADMIN)
                .build();
        when(userRepository.findByEmail(admin.getEmail()))
                .thenReturn(Optional.of(admin));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(stockRepository.findProductIdsWithStock(List.of(3L)))
                .thenReturn(List.of());
        when(purchaseOrderRepository
                .findProductIdsWithPurchaseOrders(List.of(3L)))
                .thenReturn(List.of());
        when(demandForecastRepository.findProductIdsWithForecasts(List.of(3L)))
                .thenReturn(List.of(3L));
        when(stockMovementRepository.findProductIdsWithMovementHistory(
                List.of(3L))).thenReturn(List.of());
        when(supplierProductRepository
                .findProductIdsWithSupplierMappings(List.of(3L)))
                .thenReturn(List.of());
        when(notificationRepository.findProductTargetIds(List.of(3L)))
                .thenReturn(List.of());
        when(auditEventRepository.findProductIdsWithAuditHistory(List.of(3L)))
                .thenReturn(List.of());

        List<ProductResponse> products =
                productService.getAllProducts(admin.getEmail());

        assertFalse(products.getFirst().isCanDelete());
        assertEquals(
                "Cannot delete product because it has demand forecasts.",
                products.getFirst().getDeletionBlockedReason());
        verify(stockRepository, never()).existsByProductId(3L);
        verify(purchaseOrderRepository, never()).existsByProductId(3L);
        verify(demandForecastRepository, never()).existsByProductId(3L);
        verify(stockMovementRepository, never())
                .existsByProductIdInMovementHistory(3L);
    }

    private Product deletableProduct() {
        Product product = Product.builder().id(3L).build();
        when(productRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(product));
        return product;
    }

    @Test
    void rejectsStorageFactorIncreaseThatExceedsWarehouseCapacity() {
        Product product = Product.builder()
                .id(3L)
                .name("Widget")
                .sku("W-1")
                .category("Other")
                .unit("piece")
                .price(BigDecimal.TEN)
                .storageCapacityFactor(BigDecimal.ONE)
                .build();
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Main warehouse")
                .capacity(10)
                .build();
        Stock allocation = Stock.builder()
                .warehouse(warehouse)
                .product(product)
                .quantity(2)
                .build();
        when(productRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(product));
        when(stockRepository.findByProductId(3L))
                .thenReturn(List.of(allocation));
        when(warehouseRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(warehouse));
        when(stockRepository.sumStorageCapacityByWarehouseId(2L))
                .thenReturn(BigDecimal.valueOf(9));

        ProductRequest request = new ProductRequest();
        request.setName("Widget");
        request.setSku("W-1");
        request.setCategory("Other");
        request.setUnit("piece");
        request.setPrice(BigDecimal.TEN);
        request.setStorageCapacityFactor(BigDecimal.valueOf(2));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.updateProduct(3L, request));

        assertEquals(
                "Storage capacity factor cannot be increased because it "
                        + "would exceed capacity in warehouse Main warehouse.",
                exception.getMessage());
        verify(productRepository, never()).save(product);
    }
}
