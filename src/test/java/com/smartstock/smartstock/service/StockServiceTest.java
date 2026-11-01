package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.dto.StockResponse;
import com.smartstock.smartstock.dto.StockTransferRequest;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private StockService stockService;
    private Stock stock;
    private User user;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Main warehouse")
                .capacity(10)
                .build();
        Product product = Product.builder()
                .id(3L)
                .name("Widget")
                .sku("W-1")
                .build();
        stock = Stock.builder()
                .id(4L)
                .warehouse(warehouse)
                .product(product)
                .quantity(8)
                .minQuantity(2)
                .maxQuantity(10)
                .build();
        user = User.builder()
                .id(5L)
                .email("user@smartstock.com")
                .build();

        lenient().when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.of(stock));
        lenient().when(productRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(product));
        lenient().when(warehouseRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(stock.getWarehouse()));
        lenient().when(stockRepository.sumStorageCapacityByWarehouseId(2L))
                .thenReturn(BigDecimal.valueOf(8));
        lenient().when(userRepository.findById(5L))
                .thenReturn(Optional.of(user));
    }

    @Test
    void rejectsInboundMovementAboveMaximumWithoutChangingStock() {
        StockMovementRequest request = request(
                StockMovement.MovementType.INBOUND, 3);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.recordMovement(request, 5L));

        assertEquals(
                "Incoming stock exceeds the maximum allowed. "
                        + "Current stock: 8 units; Maximum stock: 10 units; "
                        + "Available capacity: 2 units; "
                        + "Requested incoming quantity: 3 units.",
                exception.getMessage());
        assertEquals(8, stock.getQuantity());
        verifyNoInteractions(movementRepository, notificationService);
    }

    @Test
    void rejectsInboundMovementForExistingOverstockWithoutReducingIt() {
        stock.setQuantity(12);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.recordMovement(
                        request(StockMovement.MovementType.INBOUND, 1), 5L));

        assertEquals(12, stock.getQuantity());
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("Available capacity: 0 units"));
        verifyNoInteractions(movementRepository, notificationService);
    }

    @Test
    void allowsInboundUpToMaximumAndOutboundFromExistingOverstock() {
        when(movementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        stockService.recordMovement(
                request(StockMovement.MovementType.INBOUND, 2), 5L);

        assertEquals(10, stock.getQuantity());
        verify(stockRepository).save(stock);

        stock.setQuantity(12);
        stockService.recordMovement(
                request(StockMovement.MovementType.OUTBOUND, 2), 5L);

        assertEquals(10, stock.getQuantity());
    }

    @Test
    void rejectsOutboundMovementWhenStockIsInsufficient() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.recordMovement(
                        request(StockMovement.MovementType.OUTBOUND, 9), 5L));

        assertEquals("Insufficient stock. Available: 8 units",
                exception.getMessage());
        assertEquals(8, stock.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void supportsDecreaseAdjustmentsAndRejectsIncreasePastMaximum() {
        when(movementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StockMovementRequest decrease = request(
                StockMovement.MovementType.ADJUSTMENT, 3);
        decrease.setAdjustmentDirection(
                StockMovementRequest.AdjustmentDirection.DECREASE);

        stockService.recordMovement(decrease, 5L);

        assertEquals(5, stock.getQuantity());
        StockMovementRequest increase = request(
                StockMovement.MovementType.ADJUSTMENT, 6);
        increase.setAdjustmentDirection(
                StockMovementRequest.AdjustmentDirection.INCREASE);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.recordMovement(increase, 5L));

        assertEquals(5, stock.getQuantity());
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains(
                        "Adjustment increase exceeds the maximum allowed"));
        verify(movementRepository, never()).save(
                org.mockito.ArgumentMatchers.argThat(movement ->
                        movement.getQuantity() == 6));
    }

    @Test
    void rejectsInboundMovementThatExceedsWarehouseCapacity() {
        stock.setMaxQuantity(20);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.recordMovement(
                        request(StockMovement.MovementType.INBOUND, 3), 5L));

        assertEquals(8, stock.getQuantity());
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("Warehouse capacity exceeded"));
        verifyNoInteractions(movementRepository, notificationService);
    }

    @Test
    void managerTransfersFromAssignedWarehouseToAnotherDestination() {
        user.setRole(User.Role.WAREHOUSE_MGR);
        user.setWarehouse(stock.getWarehouse());
        Warehouse destinationWarehouse = Warehouse.builder()
                .id(7L)
                .name("Overflow warehouse")
                .capacity(20)
                .build();
        when(warehouseRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(destinationWarehouse));
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(7L, 3L))
                .thenReturn(Optional.empty());
        when(stockRepository.sumStorageCapacityByWarehouseId(7L))
                .thenReturn(BigDecimal.ZERO);
        when(movementRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferRequest request = new StockTransferRequest();
        request.setSourceWarehouseId(2L);
        request.setDestinationWarehouseId(7L);
        request.setProductId(3L);
        request.setQuantity(3);
        request.setReason("Balance stock");
        request.setReferenceNumber("MOVE-1");

        stockService.transfer(request, 5L);

        assertEquals(5, stock.getQuantity());
        verify(stockRepository).save(stock);
        verify(movementRepository).saveAll(org.mockito.ArgumentMatchers
                .argThat(movements -> {
                    java.util.Iterator<StockMovement> iterator =
                            movements.iterator();
                    if (!iterator.hasNext()
                            || iterator.next().getMovementType()
                            != StockMovement.MovementType.TRANSFER_OUT
                            || !iterator.hasNext()) {
                        return false;
                    }
                    return iterator.next().getMovementType()
                            == StockMovement.MovementType.TRANSFER_IN
                            && !iterator.hasNext();
                }));
    }

    @Test
    void warehouseManagerCannotTransferStockFromAnUnassignedWarehouse() {
        user.setRole(User.Role.WAREHOUSE_MGR);
        user.setWarehouse(stock.getWarehouse());
        StockTransferRequest request = new StockTransferRequest();
        request.setSourceWarehouseId(9L);
        request.setDestinationWarehouseId(7L);
        request.setProductId(3L);
        request.setQuantity(1);

        assertThrows(AccessDeniedException.class,
                () -> stockService.transfer(request, 5L));

        verifyNoInteractions(productRepository, warehouseRepository,
                movementRepository);
    }

    @Test
    void warehouseManagerCannotMoveStockInAnotherWarehouse() {
        Warehouse assignedWarehouse = Warehouse.builder()
                .id(9L)
                .name("Other warehouse")
                .build();
        user.setRole(User.Role.WAREHOUSE_MGR);
        user.setWarehouse(assignedWarehouse);
        assertThrows(AccessDeniedException.class,
                () -> stockService.recordMovement(
                        request(StockMovement.MovementType.OUTBOUND, 1), 5L));

        assertEquals(8, stock.getQuantity());
        verifyNoInteractions(movementRepository, notificationService);
    }

    @Test
    void searchesHistoryWithNoOptionalFilters() {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(movementRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        stockService.getMovementHistory(
                user.getEmail(), null, null, null, null, "   ", 0, 20);

        org.mockito.ArgumentCaptor<Pageable> pageable =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC,
                pageable.getValue().getSort()
                        .getOrderFor("createdAt").getDirection());
    }

    @Test
    void appliesAllProvidedHistoryFiltersToDynamicQuery() {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(movementRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        stockService.getMovementHistory(
                user.getEmail(),
                StockMovement.MovementType.INBOUND,
                2L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "  widget  ",
                2,
                15);

        org.mockito.ArgumentCaptor<Pageable> pageable =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(15, pageable.getValue().getPageSize());
    }

    @Test
    void exportsMovementsUsingSearchFreeQueryForBlankSearch() {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(movementRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                any(Sort.class)))
                .thenReturn(List.of());

        List<StockMovement> result = stockService.getMovementsForExport(
                user.getEmail(),
                StockMovement.MovementType.INBOUND,
                9L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                " ");

        assertEquals(List.of(), result);
        org.mockito.ArgumentCaptor<Sort> sort =
                org.mockito.ArgumentCaptor.forClass(Sort.class);
        verify(movementRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<StockMovement>>any(),
                sort.capture());
        assertEquals(Sort.Direction.DESC,
                sort.getValue().getOrderFor("createdAt").getDirection());
    }

    @Test
    void createsAllocationWhenProductIsNotYetInWarehouse() {
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.empty());
        when(stockRepository.sumStorageCapacityByWarehouseId(2L))
                .thenReturn(BigDecimal.ZERO);
        when(stockRepository.save(any(Stock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Stock allocation = allocation(5, 1, 10, stock.getWarehouse());

        Stock saved = stockService.createStock(allocation, null);

        assertEquals(5, saved.getQuantity());
        assertEquals(stock.getProduct(), saved.getProduct());
        verify(stockRepository).save(allocation);
        verify(notificationService).onStockChanged(allocation);
    }

    @Test
    void allowsSameProductAllocationInAnotherWarehouse() {
        Warehouse anotherWarehouse = Warehouse.builder()
                .id(7L)
                .name("Second warehouse")
                .capacity(10)
                .build();
        when(warehouseRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(anotherWarehouse));
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(7L, 3L))
                .thenReturn(Optional.empty());
        when(stockRepository.sumStorageCapacityByWarehouseId(7L))
                .thenReturn(BigDecimal.ZERO);
        when(stockRepository.save(any(Stock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Stock saved = stockService.createStock(
                allocation(5, 1, 10, anotherWarehouse), null);

        assertEquals(7L, saved.getWarehouse().getId());
        verify(stockRepository).save(saved);
    }

    @Test
    void rejectsDuplicateAllocationWithInboundMovementGuidance() {
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.of(stock));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.createStock(
                        allocation(5, 1, 10, stock.getWarehouse()), null));

        assertEquals(
                "This product already has a stock allocation in this "
                        + "warehouse. Use an inbound stock movement to "
                        + "increase the quantity.",
                exception.getMessage());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void rejectsAllocationQuantityAboveMaximum() {
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.createStock(
                        allocation(11, 0, 10, stock.getWarehouse()), null));

        assertEquals("Initial stock cannot exceed the product maximum.",
                exception.getMessage());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void rejectsMinimumQuantityAboveMaximum() {
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.createStock(
                        allocation(5, 11, 10, stock.getWarehouse()), null));

        assertEquals("Stock, minimum and maximum quantities are invalid.",
                exception.getMessage());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void rejectsAllocationThatExceedsWarehouseCapacity() {
        stock.getProduct().setStorageCapacityFactor(BigDecimal.valueOf(2));
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.empty());
        when(stockRepository.sumStorageCapacityByWarehouseId(2L))
                .thenReturn(BigDecimal.valueOf(9));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> stockService.createStock(
                        allocation(1, 0, 10, stock.getWarehouse()), null));

        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("Warehouse capacity exceeded"));
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    void allocationResponseUsesStockResponseFields() {
        when(stockRepository.findByWarehouseIdAndProductIdForUpdate(2L, 3L))
                .thenReturn(Optional.empty());
        when(stockRepository.sumStorageCapacityByWarehouseId(2L))
                .thenReturn(BigDecimal.ZERO);
        when(stockRepository.save(any(Stock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockResponse response = stockService.createStockResponse(
                allocation(5, 1, 10, stock.getWarehouse()), null);

        assertEquals(2L, response.getWarehouseId());
        assertEquals("Main warehouse", response.getWarehouseName());
        assertEquals(3L, response.getProductId());
        assertEquals("Widget", response.getProductName());
        assertEquals(5, response.getQuantity());
        assertEquals(1, response.getMinQuantity());
        assertEquals(10, response.getMaxQuantity());
    }

    private Stock allocation(
            int quantity, int minQuantity, int maxQuantity,
            Warehouse warehouse) {
        return Stock.builder()
                .warehouse(warehouse)
                .product(stock.getProduct())
                .quantity(quantity)
                .minQuantity(minQuantity)
                .maxQuantity(maxQuantity)
                .build();
    }

    private StockMovementRequest request(
            StockMovement.MovementType movementType,
            int quantity) {
        StockMovementRequest request = new StockMovementRequest();
        request.setWarehouseId(2L);
        request.setProductId(3L);
        request.setMovementType(movementType);
        request.setQuantity(quantity);
        return request;
    }
}
