package com.smartstock.smartstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class GroqAiService {

    // Read from application.yml
    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GroqAiService() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    // ── DEMAND FORECAST ──────────────────────────────────────────────

    public ForecastResult forecastDemand(Long productId,
                                         String productName,
                                         String category,
                                         int totalOutboundLast30Days,
                                         int totalOutboundLast7Days,
                                         int currentStock) {

        String systemPrompt = """
            You are an inventory analysis AI for a warehouse management system.
            You MUST respond ONLY with valid JSON.
            No explanation, no markdown, no text before or after.
            Never include ```json or ``` in your response.
            """;

        String userPrompt = String.format("""
            Analyze this product's inventory data and forecast demand:

            Product ID: %d
            Product Name: %s
            Category: %s
            Current Stock: %d units
            Outbound last 7 days: %d units
            Outbound last 30 days: %d units

            Return EXACTLY this JSON structure:
            {
              "forecast_next_week": <integer>,
              "forecast_next_month": <integer>,
              "confidence_score": <decimal between 0.0 and 1.0>,
              "anomaly_detected": <true or false>,
              "recommendation": "<one clear sentence>",
              "suggested_reorder_quantity": <integer>
            }
            """,
                productId,
                productName,
                category,
                currentStock,
                totalOutboundLast7Days,
                totalOutboundLast30Days
        );

        String rawResponse = callGroqApi(systemPrompt, userPrompt);

        return parseForecastResponse(rawResponse, productId);
    }

    // ── ANOMALY DETECTION ────────────────────────────────────────────

    /*
     * Currently not called by another service.
     * We keep it because anomaly detection will be used later.
     */
    public String detectAnomaly(String productName,
                                int currentStock,
                                int minQuantity,
                                int recentMovements) {

        String systemPrompt = """
            You are an inventory anomaly detection AI.
            Respond with a single sentence describing any anomaly detected.
            If no anomaly, respond: "No anomaly detected."
            """;

        String userPrompt = String.format("""
            Product: %s
            Current stock: %d units
            Minimum required: %d units
            Movements in last 7 days: %d

            Is there anything unusual about this pattern?
            """,
                productName,
                currentStock,
                minQuantity,
                recentMovements
        );

        return callGroqApi(systemPrompt, userPrompt);
    }

    // ── PRIVATE METHODS ──────────────────────────────────────────────

    // Makes the actual HTTP call to Groq API
    private String callGroqApi(String systemPrompt, String userPrompt) {

        try {

            // Build request body
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userPrompt
                            )
                    ),
                    "temperature", 0.3,
                    "max_tokens", 500
            );

            // Make HTTP POST request to Groq
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse Groq response
            JsonNode root = objectMapper.readTree(response);

            return root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

        } catch (WebClientResponseException e) {

            // Important: print the actual Groq response
            System.err.println("========================================");
            System.err.println("Groq API call failed!");
            System.err.println("Status: " + e.getStatusCode());
            System.err.println("Response: " + e.getResponseBodyAsString());
            System.err.println("========================================");

            return null;

        } catch (Exception e) {

            System.err.println("Unexpected Groq error: " + e.getMessage());

            return null;
        }
    }

    // ── PARSE FORECAST RESPONSE ─────────────────────────────────────

    private ForecastResult parseForecastResponse(String rawResponse,
                                                 Long productId) {

        if (rawResponse == null) {
            return ForecastResult.defaultResult(productId);
        }

        try {

            // Remove accidental markdown code fences
            String cleaned = rawResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode node = objectMapper.readTree(cleaned);

            return new ForecastResult(
                    productId,
                    node.path("forecast_next_week").asInt(0),
                    node.path("forecast_next_month").asInt(0),
                    node.path("confidence_score").asDouble(0.5),
                    node.path("anomaly_detected").asBoolean(false),
                    node.path("recommendation")
                            .asText("No recommendation"),
                    node.path("suggested_reorder_quantity").asInt(0)
            );

        } catch (Exception e) {

            System.err.println(
                    "Failed to parse AI response: "
                            + e.getMessage()
            );

            return ForecastResult.defaultResult(productId);
        }
    }

    // ── FORECAST RESULT ─────────────────────────────────────────────

    public record ForecastResult(
            Long productId,
            int forecastNextWeek,
            int forecastNextMonth,
            double confidenceScore,
            boolean anomalyDetected,
            String recommendation,
            int suggestedReorderQuantity
    ) {

        // Safe fallback when AI fails
        public static ForecastResult defaultResult(Long productId) {

            return new ForecastResult(
                    productId,
                    0,
                    0,
                    0.0,
                    false,
                    "Forecast unavailable — manual review required",
                    0
            );
        }
    }
}