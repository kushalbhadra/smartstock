package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ProductRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
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
    private StockService stockService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductService productService;

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
