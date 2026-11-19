package com.smartstock.smartstock.config.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.service.ForecastAccuracyService;
import com.smartstock.smartstock.service.ForecastHistoryService;
import com.smartstock.smartstock.service.ForecastGenerationException;
import com.smartstock.smartstock.service.GroqAiService;
import com.smartstock.smartstock.service.NotificationService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Configuration
public class DemandForecastJobConfig {
    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final DemandForecastRepository forecastRepository;
    private final GroqAiService groqAiService;
    private final ForecastHistoryService historyService;
    private final ForecastAccuracyService accuracyService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public DemandForecastJobConfig(
            StockRepository stockRepository,
            StockMovementRepository movementRepository,
            DemandForecastRepository forecastRepository,
            GroqAiService groqAiService,
            ForecastHistoryService historyService,
            ForecastAccuracyService accuracyService,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.forecastRepository = forecastRepository;
        this.groqAiService = groqAiService;
        this.historyService = historyService;
        this.accuracyService = accuracyService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Bean
    @StepScope
    public ItemReader<ProductForecastData> productForecastReader() {
        return new ItemReader<>() {
            private Iterator<ProductForecastData> iterator;

            @Override
            public ProductForecastData read() {
                if (iterator == null) {
                    iterator = loadForecastData().iterator();
                }
                return iterator.hasNext() ? iterator.next() : null;
            }
        };
    }

    @Bean
    public ItemProcessor<ProductForecastData, DemandForecast>
    forecastProcessor() {
        return data -> {
            Product product = new Product();
            product.setId(data.productId());
            Warehouse warehouse = new Warehouse();
            warehouse.setId(data.warehouseId());

            String historyJson = writeJson(data.historicalDemand());
            DemandForecast.DemandForecastBuilder builder = DemandForecast.builder()
                    .product(product)
                    .warehouse(warehouse)
                    .historicalDemandJson(historyJson)
                    .modelName(groqAiService.getModelName());

            if (data.historyCoverageDays()
                    < ForecastHistoryService.MINIMUM_HISTORY_DAYS) {
                return builder
                        .status(DemandForecast.ForecastStatus.INSUFFICIENT_DATA)
                        .statusMessage("Only "
                                + data.historyCoverageDays()
                                + " days of movement history are available; "
                                + ForecastHistoryService.MINIMUM_HISTORY_DAYS
                                + " are required.")
                        .build();
            }

            try {
                GroqAiService.ForecastResult result =
                        groqAiService.forecastDemand(
                                data.productId(),
                                data.productName(),
                                data.category(),
                                data.currentStock(),
                                data.historicalDemand(),
                                data.asOfDate().plusDays(1));
                return builder
                        .status(DemandForecast.ForecastStatus.SUCCESS)
                        .forecastNextDay(result.forecastNextDay())
                        .forecastNextWeek(result.forecastNextWeek())
                        .forecastNextMonth(result.forecastNextMonth())
                        .confidenceScore(result.confidenceScore())
                        .anomalyDetected(result.anomalyDetected())
                        .recommendation(result.recommendation())
                        .suggestedReorderQuantity(
                                result.suggestedReorderQuantity())
                        .dailyPredictionsJson(
                                writeJson(result.dailyPredictions()))
                        .modelName(result.modelName())
                        .build();
            } catch (ForecastGenerationException exception) {
                return builder
                        .status(DemandForecast.ForecastStatus.FAILED)
                        .statusMessage(exception.getMessage())
                        .build();
            }
        };
    }

    @Bean
    public ItemWriter<DemandForecast> forecastWriter() {
        return chunk -> {
            List<DemandForecast> items = new ArrayList<>();
            items.addAll(chunk.getItems());
            List<DemandForecast> saved = forecastRepository.saveAll(items);
            saved.stream()
                    .filter(forecast -> forecast.getStatus()
                            == DemandForecast.ForecastStatus.SUCCESS)
                    .forEach(notificationService::onForecastSaved);
            accuracyService.evaluateMaturedForecasts(LocalDate.now());
        };
    }

    @Bean
    public Step demandForecastStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("demandForecastStep", jobRepository)
                .<ProductForecastData, DemandForecast>chunk(10)
                .reader(productForecastReader())
                .processor(forecastProcessor())
                .writer(forecastWriter())
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job demandForecastJob(
            JobRepository jobRepository,
            Step demandForecastStep) {
        return new JobBuilder("demandForecastJob", jobRepository)
                .start(demandForecastStep)
                .build();
    }

    private List<ProductForecastData> loadForecastData() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(
                ForecastHistoryService.LOOKBACK_DAYS).atStartOfDay();
        LocalDateTime end = today.atStartOfDay();

        Map<AllocationKey, Map<LocalDate, Integer>> demandByDay =
                dailyOutboundDemand(start, end);
        Map<AllocationKey, LocalDate> coverageStarts =
                movementCoverageStarts();
        List<ProductForecastData> data = new ArrayList<>();

        for (Stock stock : stockRepository.findAllByOrderByIdAsc()) {
            Product product = stock.getProduct();
            Warehouse warehouse = stock.getWarehouse();
            AllocationKey key = new AllocationKey(
                    product.getId(), warehouse.getId());
            ForecastHistoryService.ForecastHistory history =
                    historyService.build(today, coverageStarts.get(key),
                            demandByDay.getOrDefault(key, Map.of()));
            data.add(new ProductForecastData(
                    product.getId(),
                    product.getName(),
                    product.getCategory(),
                    warehouse.getId(),
                    warehouse.getName(),
                    today,
                    stock.getQuantity(),
                    history.coverageDays(),
                    history.dailyDemand()));
        }
        return data;
    }

    private Map<AllocationKey, Map<LocalDate, Integer>> dailyOutboundDemand(
            LocalDateTime start, LocalDateTime end) {
        Map<AllocationKey, Map<LocalDate, Integer>> result = new HashMap<>();
        for (Object[] row : movementRepository
                .getDailyDemandByProductAndWarehouse(
                        start, end, StockMovement.MovementType.OUTBOUND)) {
            AllocationKey key = new AllocationKey(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue());
            LocalDate date = toLocalDateTime(row[2]).toLocalDate();
            int units = Math.toIntExact(((Number) row[3]).longValue());
            result.computeIfAbsent(key, ignored -> new HashMap<>())
                    .put(date, units);
        }
        return result;
    }

    private Map<AllocationKey, LocalDate> movementCoverageStarts() {
        Map<AllocationKey, LocalDate> result = new HashMap<>();
        for (Object[] row :
                movementRepository
                        .getMovementCoverageStartByProductAndWarehouse()) {
            result.put(new AllocationKey(
                            ((Number) row[0]).longValue(),
                            ((Number) row[1]).longValue()),
                    toLocalDateTime(row[2]).toLocalDate());
        }
        return result;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException(
                "Unexpected timestamp type in forecast movement query.");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize forecast data.", exception);
        }
    }

    private record AllocationKey(Long productId, Long warehouseId) {
    }
}
