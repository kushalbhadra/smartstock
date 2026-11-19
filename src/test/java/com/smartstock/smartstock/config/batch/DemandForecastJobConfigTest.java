package com.smartstock.smartstock.config.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartstock.smartstock.dto.ForecastDataPoint;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.service.ForecastAccuracyService;
import com.smartstock.smartstock.service.ForecastGenerationException;
import com.smartstock.smartstock.service.ForecastHistoryService;
import com.smartstock.smartstock.service.GroqAiService;
import com.smartstock.smartstock.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandForecastJobConfigTest {
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private DemandForecastRepository forecastRepository;
    @Mock
    private GroqAiService groqAiService;
    @Mock
    private ForecastHistoryService historyService;
    @Mock
    private ForecastAccuracyService accuracyService;
    @Mock
    private NotificationService notificationService;

    private ItemProcessor<ProductForecastData, DemandForecast> processor;

    @BeforeEach
    void setUp() {
        DemandForecastJobConfig config = new DemandForecastJobConfig(
                stockRepository,
                movementRepository,
                forecastRepository,
                groqAiService,
                historyService,
                accuracyService,
                notificationService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        processor = config.forecastProcessor();
    }

    @Test
    void persistsFailedStatusWithoutFakeZeroPredictions() throws Exception {
        when(groqAiService.getModelName()).thenReturn("test-model");
        when(groqAiService.forecastDemand(
                eq(1L), eq("Widget"), eq("General"), eq(8),
                any(), any()))
                .thenThrow(new ForecastGenerationException(
                        "Groq service could not be reached."));

        DemandForecast forecast = processor.process(data(
                ForecastHistoryService.MINIMUM_HISTORY_DAYS));

        assertEquals(DemandForecast.ForecastStatus.FAILED,
                forecast.getStatus());
        assertNull(forecast.getForecastNextDay());
        assertNull(forecast.getForecastNextWeek());
        assertNull(forecast.getForecastNextMonth());
        assertNull(forecast.getConfidenceScore());
        assertNull(forecast.getSuggestedReorderQuantity());
        assertNull(forecast.getDailyPredictionsJson());
        assertEquals("Groq service could not be reached.",
                forecast.getStatusMessage());
    }

    @Test
    void persistsInsufficientHistoryWithoutCallingGroq() throws Exception {
        when(groqAiService.getModelName()).thenReturn("test-model");
        DemandForecast forecast = processor.process(data(12));

        assertEquals(DemandForecast.ForecastStatus.INSUFFICIENT_DATA,
                forecast.getStatus());
        assertNull(forecast.getForecastNextDay());
        assertNull(forecast.getForecastNextWeek());
        assertNull(forecast.getForecastNextMonth());
        verify(groqAiService, never()).forecastDemand(
                any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void forecastWriterDoesNotChangeStock() throws Exception {
        Product product = new Product();
        product.setId(1L);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        DemandForecast failedForecast = DemandForecast.builder()
                .product(product)
                .warehouse(warehouse)
                .status(DemandForecast.ForecastStatus.FAILED)
                .build();
        when(forecastRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Chunk<DemandForecast> chunk = new Chunk<>();
        chunk.add(failedForecast);

        ItemWriter<DemandForecast> writer =
                new DemandForecastJobConfig(
                        stockRepository,
                        movementRepository,
                        forecastRepository,
                        groqAiService,
                        historyService,
                        accuracyService,
                        notificationService,
                        new ObjectMapper().registerModule(
                                new JavaTimeModule()))
                        .forecastWriter();
        writer.write(chunk);

        verify(stockRepository, never()).save(any(Stock.class));
        verify(notificationService, never()).onForecastSaved(failedForecast);
    }

    private ProductForecastData data(int coverageDays) {
        List<ForecastDataPoint> history = new ArrayList<>();
        for (int day = coverageDays; day > 0; day--) {
            history.add(new ForecastDataPoint(
                    LocalDate.now().minusDays(day), day % 3));
        }
        return new ProductForecastData(
                1L,
                "Widget",
                "General",
                2L,
                "North warehouse",
                LocalDate.now(),
                8,
                coverageDays,
                history);
    }
}
