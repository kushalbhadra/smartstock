package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:forecast-demand;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class StockMovementDemandRepositoryTest {
    @Autowired
    private StockMovementRepository movementRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void aggregatesOutboundOnlyByProductWarehouseAndDay() {
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("Demand item")
                .sku("DEMAND-1")
                .category("General")
                .unit("piece")
                .price(BigDecimal.ONE)
                .build());
        Warehouse warehouse = warehouseRepository.saveAndFlush(
                Warehouse.builder()
                        .name("North")
                        .location("North")
                        .capacity(100)
                        .build());
        Stock stock = stockRepository.saveAndFlush(Stock.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(20)
                .minQuantity(2)
                .maxQuantity(50)
                .build());
        User user = userRepository.saveAndFlush(User.builder()
                .name("Test Admin")
                .email("forecast-admin@example.invalid")
                .password("test-hash")
                .role(User.Role.ADMIN)
                .build());

        saveMovement(stock, user, StockMovement.MovementType.OUTBOUND, 4);
        saveMovement(stock, user, StockMovement.MovementType.OUTBOUND, 6);
        saveMovement(stock, user, StockMovement.MovementType.OUTBOUND, -2);
        saveMovement(stock, user, StockMovement.MovementType.TRANSFER_OUT, 20);
        saveMovement(stock, user, StockMovement.MovementType.ADJUSTMENT, 3);
        saveMovement(stock, user, StockMovement.MovementType.INBOUND, 8);
        movementRepository.flush();

        LocalDateTime start = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        List<Object[]> rows =
                movementRepository.getDailyDemandByProductAndWarehouse(
                        start, end, StockMovement.MovementType.OUTBOUND);

        assertEquals(1, rows.size());
        assertEquals(product.getId(), ((Number) rows.getFirst()[0]).longValue());
        assertEquals(warehouse.getId(),
                ((Number) rows.getFirst()[1]).longValue());
        assertEquals(10, ((Number) rows.getFirst()[3]).intValue());
    }

    private void saveMovement(
            Stock stock,
            User user,
            StockMovement.MovementType type,
            int quantity) {
        movementRepository.save(StockMovement.builder()
                .stock(stock)
                .movementType(type)
                .quantity(quantity)
                .performedBy(user)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
