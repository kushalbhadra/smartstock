package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DemandForecastRepositoryTest {
    @Autowired
    private DemandForecastRepository forecastRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void persistsDailyForecastAndWarehouseWithoutChangingLegacyValues() {
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("Forecast item")
                .sku("FORECAST-1")
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

        DemandForecast forecast = forecastRepository.saveAndFlush(
                DemandForecast.builder()
                        .product(product)
                        .warehouse(warehouse)
                        .forecastNextDay(2)
                        .forecastNextWeek(14)
                        .forecastNextMonth(60)
                        .confidenceScore(new BigDecimal("0.80"))
                        .anomalyDetected(false)
                        .recommendation("Review supplier lead time.")
                        .suggestedReorderQuantity(10)
                        .status(DemandForecast.ForecastStatus.SUCCESS)
                        .modelName("test-model")
                        .historicalDemandJson("[{\"date\":\"2026-11-16\",\"units\":3}]")
                        .dailyPredictionsJson("[{\"date\":\"2026-11-18\",\"units\":2}]")
                        .accuracyMae(new BigDecimal("1.2500"))
                        .accuracyWape(new BigDecimal("0.500000"))
                        .accuracyDays(7)
                        .accuracyComparisonJson("[]")
                        .build());

        DemandForecast loaded = forecastRepository
                .findById(forecast.getId())
                .orElseThrow();

        assertEquals(2, loaded.getForecastNextDay());
        assertEquals(14, loaded.getForecastNextWeek());
        assertEquals(60, loaded.getForecastNextMonth());
        assertEquals(DemandForecast.ForecastStatus.SUCCESS,
                loaded.getStatus());
        assertEquals("test-model", loaded.getModelName());
        assertEquals(warehouse.getId(), loaded.getWarehouse().getId());
        assertEquals("[]", loaded.getAccuracyComparisonJson());
        assertNotNull(loaded.getCreatedAt());

        DemandForecast latest = forecastRepository.saveAndFlush(
                DemandForecast.builder()
                        .product(product)
                        .warehouse(warehouse)
                        .forecastNextDay(5)
                        .status(DemandForecast.ForecastStatus.FAILED)
                        .statusMessage("Groq service could not be reached.")
                        .build());
        assertEquals(1, forecastRepository
                .findLatestForecastsByProductAndWarehouse().size());
        assertEquals(latest.getId(), forecastRepository
                .findLatestForecastsByProductAndWarehouse()
                .getFirst().getId());
        assertEquals(latest.getId(), forecastRepository
                .findLatestForecastsByWarehouseId(warehouse.getId())
                .getFirst().getId());
    }
}
