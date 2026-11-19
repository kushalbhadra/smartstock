package com.smartstock.smartstock.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastHistoryServiceTest {
    private final ForecastHistoryService service =
            new ForecastHistoryService();

    @Test
    void fillsNoDemandDaysWithZeroOnlyAfterHistoryCoverageBegins() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        LocalDate firstMovement = today.minusDays(31);

        ForecastHistoryService.ForecastHistory history = service.build(
                today,
                firstMovement,
                Map.of(today.minusDays(2), 5));

        assertEquals(31, history.coverageDays());
        assertEquals(31, history.dailyDemand().size());
        assertEquals(5, history.dailyDemand().get(29).units());
        assertEquals(0, history.dailyDemand().get(28).units());
        assertTrue(history.hasSufficientData());
    }

    @Test
    void marksRecentMovementHistoryAsInsufficient() {
        LocalDate today = LocalDate.of(2026, 11, 17);

        ForecastHistoryService.ForecastHistory history = service.build(
                today,
                today.minusDays(12),
                Map.of(today.minusDays(2), 4));

        assertEquals(12, history.coverageDays());
        assertFalse(history.hasSufficientData());
    }

    @Test
    void doesNotInventZeroHistoryWhenNoMovementWasRecorded() {
        ForecastHistoryService.ForecastHistory history = service.build(
                LocalDate.of(2026, 11, 17), null, Map.of());

        assertEquals(0, history.coverageDays());
        assertTrue(history.dailyDemand().isEmpty());
        assertFalse(history.hasSufficientData());
    }
}
