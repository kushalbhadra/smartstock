package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.SupplierProductRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.SupplierProduct;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.SupplierProductRepository;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private SupplierProductRepository supplierProductRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    void listsMappingsWithSupplierAndProductDetailsForAdminPage() {
        SupplierProduct mapping = mapping(true);
        when(supplierProductRepository.findAllWithSupplierAndProduct())
                .thenReturn(List.of(mapping));

        var result = supplierService.getAllSupplierProductMappings();

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getSupplierId());
        assertEquals("Supplier A", result.get(0).getSupplierName());
        assertEquals(3L, result.get(0).getProductId());
        assertEquals("Widget", result.get(0).getProductName());
        assertEquals(new BigDecimal("12.50"),
                result.get(0).getPurchasePrice());
        assertEquals(true, result.get(0).isActive());
    }

    @Test
    void createsOnlyTheExplicitSupplierProductMapping() {
        Supplier supplier = Supplier.builder().id(4L).name("Supplier A").build();
        Product product = Product.builder()
                .id(3L).name("Widget").sku("W-1").build();
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(product));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.empty());
        when(supplierProductRepository.save(any(SupplierProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SupplierProductRequest request = new SupplierProductRequest();
        request.setProductId(3L);

        var response = supplierService.addSupplierProduct(4L, request);

        assertEquals(3L, response.getProductId());
        assertEquals("Widget", response.getProductName());
        assertEquals("W-1", response.getSku());
        assertEquals(true, response.isActive());
        verify(supplierProductRepository).save(any(SupplierProduct.class));
    }

    @Test
    void reactivatesAnExistingInactiveMapping() {
        SupplierProduct mapping = mapping(false);
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(mapping.getSupplier()));
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(mapping.getProduct()));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.of(mapping));
        when(supplierProductRepository.save(mapping)).thenReturn(mapping);

        SupplierProductRequest request = new SupplierProductRequest();
        request.setProductId(3L);

        var response = supplierService.addSupplierProduct(4L, request);

        assertEquals(true, response.isActive());
        assertEquals(new BigDecimal("12.50"), response.getPurchasePrice());
        assertEquals(5, response.getLeadTimeDays());
        assertEquals("SUP-W-1", response.getSupplierProductCode());
        verify(supplierProductRepository).save(mapping);
    }

    @Test
    void rejectsDuplicateActiveMapping() {
        SupplierProduct mapping = mapping(true);
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(mapping.getSupplier()));
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(mapping.getProduct()));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(4L, 3L))
                .thenReturn(Optional.of(mapping));

        SupplierProductRequest request = new SupplierProductRequest();
        request.setProductId(3L);

        assertThrows(BusinessException.class,
                () -> supplierService.addSupplierProduct(4L, request));
        verify(supplierProductRepository, never())
                .save(any(SupplierProduct.class));
    }

    @Test
    void managerSeesOnlyActiveSupplierProductsStockedInAssignedWarehouse() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(Warehouse.builder().id(2L).build())
                .build();
        Supplier supplier = Supplier.builder().id(4L).name("Supplier A")
                .build();
        when(supplierRepository.findById(4L))
                .thenReturn(Optional.of(supplier));
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(supplierProductRepository
                .findAllBySupplier_IdAndActiveTrueOrderByProduct_NameAsc(4L))
                .thenReturn(List.of(
                        mapping(true),
                        SupplierProduct.builder()
                                .id(9L)
                                .supplier(supplier)
                                .product(Product.builder()
                                        .id(5L)
                                        .name("Unstocked")
                                        .sku("W-2")
                                        .build())
                                .active(true)
                                .build()));
        when(stockRepository.findProductIdsByWarehouseId(2L))
                .thenReturn(List.of(3L));

        var result = supplierService.getActiveSupplierProducts(
                4L, manager.getEmail());

        assertEquals(List.of(3L),
                result.stream().map(item -> item.getProductId()).toList());
    }

    private SupplierProduct mapping(boolean active) {
        return SupplierProduct.builder()
                .id(8L)
                .supplier(Supplier.builder()
                        .id(4L).name("Supplier A").build())
                .product(Product.builder()
                        .id(3L).name("Widget").sku("W-1").build())
                .purchasePrice(new BigDecimal("12.50"))
                .leadTimeDays(5)
                .supplierProductCode("SUP-W-1")
                .active(active)
                .build();
    }
}
