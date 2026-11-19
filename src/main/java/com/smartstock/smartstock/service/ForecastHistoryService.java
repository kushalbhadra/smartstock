package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ForecastDataPoint;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ForecastHistoryService {
    public static final int LOOKBACK_DAYS = 90;
    public static final int MINIMUM_HISTORY_DAYS = 30;

    public ForecastHistory build(
            LocalDate today,
            LocalDate firstRecordedMovement,
            Map<LocalDate, Integer> outboundDemandByDay) {
        if (firstRecordedMovement == null) {
            return new ForecastHistory(0, List.of());
        }

        LocalDate start = firstRecordedMovement.isAfter(
                today.minusDays(LOOKBACK_DAYS))
                ? firstRecordedMovement
                : today.minusDays(LOOKBACK_DAYS);
        int coverageDays = Math.toIntExact(
                Math.max(0, ChronoUnit.DAYS.between(start, today)));

        List<ForecastDataPoint> points = new ArrayList<>(coverageDays);
        for (LocalDate date = start; date.isBefore(today);
             date = date.plusDays(1)) {
            points.add(new ForecastDataPoint(
                    date, outboundDemandByDay.getOrDefault(date, 0)));
        }
        return new ForecastHistory(coverageDays, List.copyOf(points));
    }

    public record ForecastHistory(
            int coverageDays,
            List<ForecastDataPoint> dailyDemand) {
        public boolean hasSufficientData() {
            return coverageDays >= MINIMUM_HISTORY_DAYS;
        }
    }
}
