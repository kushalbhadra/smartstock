package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.StockMovementRequest;
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

import java.util.Optional;
import java.math.BigDecimal;

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
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
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
    void transfersStockAtomicallyAndCreatesPairedMovements() {
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
