package com.smartstock.smartstock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.smartstock.dto.ForecastAccuracyPoint;
import com.smartstock.smartstock.dto.ForecastDataPoint;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastAccuracyService {
    private static final int VALIDATION_HORIZON_DAYS = 7;
    private static final int CANDIDATE_LOOKBACK_DAYS = 60;

    private final DemandForecastRepository forecastRepository;
    private final StockMovementRepository movementRepository;
    private final ObjectMapper objectMapper;

    public ForecastAccuracyService(
            DemandForecastRepository forecastRepository,
            StockMovementRepository movementRepository,
            ObjectMapper objectMapper) {
        this.forecastRepository = forecastRepository;
        this.movementRepository = movementRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluateMaturedForecasts(LocalDate today) {
        List<DemandForecast> candidates =
                forecastRepository
                        .findTop500ByStatusAndAccuracyEvaluatedAtIsNullAndCreatedAtAfterAndWarehouseIsNotNullOrderByCreatedAtAsc(
                                DemandForecast.ForecastStatus.SUCCESS,
                                today.minusDays(CANDIDATE_LOOKBACK_DAYS)
                                        .atStartOfDay());
        if (candidates.isEmpty()) {
            return;
        }

        LocalDateTime start = today.minusDays(CANDIDATE_LOOKBACK_DAYS)
                .atStartOfDay();
        Map<AllocationKey, Map<LocalDate, Integer>> demand =
                dailyOutboundDemand(start, today.atStartOfDay());
        Map<AllocationKey, LocalDate> coverageStarts =
                movementCoverageStarts();

        for (DemandForecast forecast : candidates) {
            AllocationKey key = new AllocationKey(
                    forecast.getProduct().getId(),
                    forecast.getWarehouse().getId());
            LocalDate coverageStart = coverageStarts.get(key);
            List<ForecastDataPoint> predictions =
                    readDailyPredictions(forecast.getDailyPredictionsJson());
            if (coverageStart == null
                    || predictions.size() < VALIDATION_HORIZON_DAYS) {
                continue;
            }

            List<ForecastAccuracyPoint> comparison =
                    new ArrayList<>(VALIDATION_HORIZON_DAYS);
            boolean complete = true;
            long absoluteErrorTotal = 0;
            long actualTotal = 0;
            for (int index = 0; index < VALIDATION_HORIZON_DAYS; index++) {
                ForecastDataPoint prediction = predictions.get(index);
                if (!prediction.date().isBefore(today)
                        || prediction.date().isBefore(coverageStart)) {
                    complete = false;
                    break;
                }
                int actual = demand.getOrDefault(key, Map.of())
                        .getOrDefault(prediction.date(), 0);
                comparison.add(new ForecastAccuracyPoint(
                        prediction.date(), actual, prediction.units()));
                absoluteErrorTotal += Math.abs(
                        (long) prediction.units() - actual);
                actualTotal += actual;
            }
            if (!complete) {
                continue;
            }

            forecast.setAccuracyDays(VALIDATION_HORIZON_DAYS);
            forecast.setAccuracyMae(BigDecimal.valueOf(absoluteErrorTotal)
                    .divide(BigDecimal.valueOf(VALIDATION_HORIZON_DAYS),
                            4, RoundingMode.HALF_UP));
            forecast.setAccuracyWape(actualTotal == 0 ? null
                    : BigDecimal.valueOf(absoluteErrorTotal)
                    .divide(BigDecimal.valueOf(actualTotal), 6,
                            RoundingMode.HALF_UP));
            forecast.setAccuracyComparisonJson(writeJson(comparison));
            forecast.setAccuracyEvaluatedAt(LocalDateTime.now());
        }
        forecastRepository.saveAll(candidates);
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

    private List<ForecastDataPoint> readDailyPredictions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored forecast predictions contain invalid JSON.",
                    exception);
        }
    }

    private String writeJson(List<ForecastAccuracyPoint> comparison) {
        try {
            return objectMapper.writeValueAsString(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize forecast accuracy results.",
                    exception);
        }
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

    private record AllocationKey(Long productId, Long warehouseId) {
    }
}
