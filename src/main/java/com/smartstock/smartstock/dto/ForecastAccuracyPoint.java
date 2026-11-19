package com.smartstock.smartstock.dto;

import java.time.LocalDate;

public record ForecastAccuracyPoint(
        LocalDate date,
        int actualUnits,
        int predictedUnits) {
}
