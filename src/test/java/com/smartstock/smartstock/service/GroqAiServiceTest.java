package com.smartstock.smartstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartstock.smartstock.dto.ForecastDataPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroqAiServiceTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    private HttpServer server;
    private GroqAiService service;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        service = new GroqAiService(
                objectMapper,
                "test-api-key",
                "http://127.0.0.1:" + server.getAddress().getPort()
                        + "/forecast",
                "test-model");
        responseStatus = 200;
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void derivesDailyWeeklyAndMonthlyTotalsFromValidatedDailyOutput()
            throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 18);
        String forecastJson = validForecast(start, 1);
        responseBody = objectMapper.writeValueAsString(
                envelope(forecastJson));

        GroqAiService.ForecastResult result = service.forecastDemand(
                7L, "Product A", "General", 20,
                List.of(new ForecastDataPoint(start.minusDays(1), 2)), start);

        assertEquals(1, result.forecastNextDay());
        assertEquals(28, result.forecastNextWeek());
        assertEquals(465, result.forecastNextMonth());
        assertEquals("test-model", result.modelName());
        assertEquals(30, result.dailyPredictions().size());
    }

    @Test
    void apiFailureIsNotConvertedIntoZeroForecast() throws Exception {
        responseStatus = 503;
        responseBody = "unavailable";

        ForecastGenerationException exception = assertThrows(
                ForecastGenerationException.class,
                () -> service.forecastDemand(
                        7L, "Product A", "General", 20, List.of(),
                        LocalDate.of(2026, 11, 18)));

        assertEquals("Groq request failed with HTTP 503.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedForecastJson() throws Exception {
        responseBody = objectMapper.writeValueAsString(
                envelope("{not-json"));

        ForecastGenerationException exception = assertThrows(
                ForecastGenerationException.class,
                () -> service.forecastDemand(
                        7L, "Product A", "General", 20, List.of(),
                        LocalDate.of(2026, 11, 18)));

        assertEquals("Groq returned invalid forecast JSON.",
                exception.getMessage());
    }

    @Test
    void rejectsIncorrectPredictionDatesAndNegativeDemand() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 18);
        String wrongDates = validForecast(start.plusDays(1), 1);
        responseBody = objectMapper.writeValueAsString(envelope(wrongDates));
        assertThrows(ForecastGenerationException.class,
                () -> service.forecastDemand(
                        7L, "Product A", "General", 20, List.of(), start));

        responseBody = objectMapper.writeValueAsString(
                envelope(validForecast(start, -1)));
        assertThrows(ForecastGenerationException.class,
                () -> service.forecastDemand(
                        7L, "Product A", "General", 20, List.of(), start));
    }

    private static String validForecast(LocalDate start, int firstUnits) {
        StringBuilder points = new StringBuilder();
        for (int day = 0; day < 30; day++) {
            if (day > 0) points.append(',');
            points.append("""
                    {"date":"%s","units":%d}
                    """.formatted(start.plusDays(day), firstUnits + day).trim());
        }
        return """
                {
                  "daily_predictions": [%s],
                  "confidence_score": 0.8,
                  "anomaly_detected": false,
                  "recommendation": "Review supplier lead time.",
                  "suggested_reorder_quantity": 12
                }
                """.formatted(points);
    }

    private static Object envelope(String content) {
        return java.util.Map.of(
                "choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", content))));
    }
}
