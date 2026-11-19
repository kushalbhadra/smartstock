package com.smartstock.smartstock.dto;

import java.time.LocalDate;

public record ForecastDataPoint(LocalDate date, int units) {
}
