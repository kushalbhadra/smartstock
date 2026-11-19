package com.smartstock.smartstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartstock.smartstock.dto.ForecastDataPoint;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastAccuracyServiceTest {
    @Mock
    private DemandForecastRepository forecastRepository;
    @Mock
    private StockMovementRepository movementRepository;

    private ForecastAccuracyService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        service = new ForecastAccuracyService(
                forecastRepository, movementRepository, mapper);
    }

    @Test
    void calculatesMaeAndWapeFromSevenCompletedActualDays()
            throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 17);
        DemandForecast forecast = successfulForecast(today, 2);
        when(forecastRepository
                .findTop500ByStatusAndAccuracyEvaluatedAtIsNullAndCreatedAtAfterAndWarehouseIsNotNullOrderByCreatedAtAsc(
                        eq(DemandForecast.ForecastStatus.SUCCESS), any()))
                .thenReturn(List.of(forecast));
        when(movementRepository.getDailyDemandByProductAndWarehouse(
                any(), any(), any()))
                .thenReturn(demandRows(today, new int[]{3, 0, 2, 0, 4, 0, 1}));
        when(movementRepository
                .getMovementCoverageStartByProductAndWarehouse())
                .thenReturn(java.util.Collections.singletonList(new Object[]{
                        1L, 2L, today.minusDays(30).atStartOfDay()}));

        service.evaluateMaturedForecasts(today);

        assertEquals(7, forecast.getAccuracyDays());
        assertEquals("1.4286", forecast.getAccuracyMae().toPlainString());
        assertEquals("1.000000", forecast.getAccuracyWape().toPlainString());
        assertEquals(7, new ObjectMapper()
                .readTree(forecast.getAccuracyComparisonJson()).size());
        verify(forecastRepository).saveAll(any());
    }

    @Test
    void leavesWapeUndefinedWhenAllActualDemandIsZero() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 17);
        DemandForecast forecast = successfulForecast(today, 2);
        when(forecastRepository
                .findTop500ByStatusAndAccuracyEvaluatedAtIsNullAndCreatedAtAfterAndWarehouseIsNotNullOrderByCreatedAtAsc(
                        eq(DemandForecast.ForecastStatus.SUCCESS), any()))
                .thenReturn(List.of(forecast));
        when(movementRepository.getDailyDemandByProductAndWarehouse(
                any(), any(), any())).thenReturn(List.of());
        when(movementRepository
                .getMovementCoverageStartByProductAndWarehouse())
                .thenReturn(java.util.Collections.singletonList(new Object[]{
                        1L, 2L, today.minusDays(30).atStartOfDay()}));

        service.evaluateMaturedForecasts(today);

        assertEquals(2, forecast.getAccuracyMae().intValue());
        assertNull(forecast.getAccuracyWape());
    }

    private DemandForecast successfulForecast(LocalDate today, int units)
            throws Exception {
        Product product = new Product();
        product.setId(1L);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        List<ForecastDataPoint> predictions = new ArrayList<>();
        for (int day = 0; day < 30; day++) {
            predictions.add(new ForecastDataPoint(
                    today.minusDays(7).plusDays(day), units));
        }
        String predictionJson = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(predictions);
        return DemandForecast.builder()
                .product(product)
                .warehouse(warehouse)
                .status(DemandForecast.ForecastStatus.SUCCESS)
                .dailyPredictionsJson(predictionJson)
                .createdAt(today.minusDays(8).atStartOfDay())
                .build();
    }

    private static List<Object[]> demandRows(LocalDate today, int[] units) {
        List<Object[]> rows = new ArrayList<>();
        for (int day = 0; day < units.length; day++) {
            if (units[day] > 0) {
                rows.add(new Object[]{
                        1L, 2L, today.minusDays(7 - day).atStartOfDay(),
                        units[day]});
            }
        }
        return rows;
    }
}
