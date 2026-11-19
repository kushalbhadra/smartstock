package com.smartstock.smartstock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.smartstock.dto.DemandForecastResponse;
import com.smartstock.smartstock.dto.ForecastAccuracyPoint;
import com.smartstock.smartstock.dto.ForecastDataPoint;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DemandForecastService {
    private final DemandForecastRepository forecastRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DemandForecastService(
            DemandForecastRepository forecastRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.forecastRepository = forecastRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DemandForecastResponse> getForecasts(String email) {
        User user = findUser(email);
        List<DemandForecast> forecasts;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            if (user.getWarehouse() == null) {
                return List.of();
            }
            forecasts = forecastRepository
                    .findByWarehouseIdOrderByCreatedAtDesc(
                            user.getWarehouse().getId());
        } else {
            forecasts = forecastRepository.findAllByOrderByCreatedAtDesc();
        }
        return forecasts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DemandForecastResponse> getCurrentForecasts(String email) {
        User user = findUser(email);
        List<DemandForecast> forecasts;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            if (user.getWarehouse() == null) {
                return List.of();
            }
            forecasts = forecastRepository.findLatestForecastsByWarehouseId(
                    user.getWarehouse().getId());
        } else {
            forecasts = forecastRepository
                    .findLatestForecastsByProductAndWarehouse();
        }
        return forecasts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DemandForecastResponse> getForecast(
            Long productId, String email) {
        User user = findUser(email);
        Optional<DemandForecast> forecast =
                user.getRole() == User.Role.WAREHOUSE_MGR
                        ? user.getWarehouse() == null
                        ? Optional.empty()
                        : forecastRepository
                        .findTopByProductIdAndWarehouseIdOrderByCreatedAtDesc(
                                productId, user.getWarehouse().getId())
                        : forecastRepository
                        .findTopByProductIdOrderByCreatedAtDesc(productId);
        return forecast.map(this::toResponse);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user was not found."));
    }

    private DemandForecastResponse toResponse(DemandForecast forecast) {
        return new DemandForecastResponse(
                forecast.getId(),
                new DemandForecastResponse.ProductSummary(
                        forecast.getProduct().getId(),
                        forecast.getProduct().getName(),
                        forecast.getProduct().getSku(),
                        forecast.getProduct().getCategory(),
                        forecast.getProduct().getUnit(),
                        forecast.getProduct().getPrice(),
                        forecast.getProduct().getStorageCapacityFactor(),
                        forecast.getProduct().getCreatedAt(),
                        forecast.getProduct().getUpdatedAt()),
                forecast.getWarehouse() == null
                        ? null : forecast.getWarehouse().getId(),
                forecast.getWarehouse() == null
                        ? null : forecast.getWarehouse().getName(),
                forecast.getForecastNextDay(),
                forecast.getForecastNextWeek(),
                forecast.getForecastNextMonth(),
                forecast.getConfidenceScore(),
                forecast.getAnomalyDetected(),
                forecast.getRecommendation(),
                forecast.getSuggestedReorderQuantity(),
                forecast.getStatus(),
                forecast.getStatusMessage(),
                forecast.getModelName(),
                readJson(forecast.getHistoricalDemandJson(),
                        new TypeReference<>() {
                        }),
                readJson(forecast.getDailyPredictionsJson(),
                        new TypeReference<>() {
                        }),
                forecast.getAccuracyMae(),
                forecast.getAccuracyWape(),
                forecast.getAccuracyDays(),
                readJson(forecast.getAccuracyComparisonJson(),
                        new TypeReference<>() {
                        }),
                forecast.getCreatedAt());
    }

    private <T> List<T> readJson(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored demand forecast data contains invalid JSON.",
                    exception);
        }
    }
}
